/* AutoPATT version 0.7 */

import au.com.bytecode.opencsv.CSVWriter;

import java.text.SimpleDateFormat;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
//Do not import java.awt.* as this creates a type exception with List
import java.awt.Frame;
import java.awt.FileDialog;

import ca.phon.ipa.*;
import ca.phon.ipa.parser.*;
import ca.phon.app.session.*;
import ca.phon.ipa.features.IPAElementComparator;
import ca.phon.ipa.tree.*;
import ca.phon.phonex.*;


def AutoPATTversion = "0.7"

class PhoneticInventory { 
	static private IPATokens ipaTokens = new IPATokens()
	private Map inventoryMap
	
	/* create a PhoneticInventory from Phon records */
	PhoneticInventory(records) {
		inventoryMap = [:]
		records.each{ record ->
			record.IPAActual.each { transcript ->
				transcript.findAll { it instanceof Phone}.each {
					if ( isConsonant(it) ) {
						def phone = it.text
						/* Count occurrences of each phone */
						def i = inventoryMap[phone]
						inventoryMap[phone] = i ? ++i : 1
					}
				}
			}
		}
	}
	
	List getInventory() {
	/* Collect all phones that occur more than once */
		return this.inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}
	
	static boolean isConsonant(c) {
  	  if ( !(c instanceof Phone)) 
		  return false
	  
	  def tokenType = ipaTokens.getTokenType( c.getBasePhone() );
	  if ( tokenType == IPATokenType.CONSONANT || 
	  	  tokenType == IPATokenType.GLIDE ) 
	    return true
	  else
		return false
	}
}

class PhonemicInventory { 
	private Map inventoryMap, meanings
	private PhoneticInventory phoneticInv
	private IpaTernaryTree minPairs
	protected PrintWriter out

	PhonemicInventory(records, out) {
		this.out = out
		this.inventoryMap = [:]
		this.meanings = [:]
		this.phoneticInv = new PhoneticInventory(records)
		this.minPairs = new IpaTernaryTree( 
		  new IPAElementComparator())
		
		/* First initialize minPair HashSets and populate meanings Map */
		records.each { record ->
			record.orthography.eachWithIndex { tier, index ->
				def words = tier.toString().tokenize()
				/* If there are more orthos than productions...*/
				if (words.count{it} != record.IPAActual[index].words().count{it}) {
					//out.println "Orthography <-> IPA Actual Count Mismatch!"
				}
				record.IPAActual[index].words().findAll{it.contains("\\w")}.eachWithIndex { utt, i ->
					//every word in the word tree gets an empty hash set.
					this.minPairs.put(utt, new HashSet<IPATranscript>());
					//out.println "meanings[$utt] = ${words[i]}"
					/*map the utterance to orthography. This is too rudimentary. What
					if the child has the same utterance for different meanings? What
					about homonyms? It needs to be a mapping to a list, not a string. 
					It's not a 1:1 mapping*/
					this.meanings[utt.toString()] = words[i];
				}
			}
		}
		
		/* Now populate minPairs */
		buildMinPairs()
		
		/* Now build phonemic inventory */
		def wordKeys = minPairs.keySet()
		def doneContrast = [] /* For rule 4 */
		
		wordKeys.each { key ->
			def pairs = this.minPairs.get(key);
			
			pairs.each { pair ->
				/* Go through each phone in both keys until we find contrast */
				for (i in 0 .. key.length() - 1) {
					/* phones to compare */
					def p1 = key[i]
					def p2 = pair[i]
					
					if ( PhoneticInventory.isConsonant(p1) &&
					PhoneticInventory.isConsonant(p2) && 
				    this.phoneticInv.inventory.contains(p1.text) &&
				    this.meanings[key.toString()] != this.meanings[pair.toString()] ) {
						/* if the consonants don't match and rule 4 is satisfied
						we should have a minimal pair with a consonant contrast
						here */
						if ( p1.text != p2.text && !(doneContrast[i])) {  
						  /* out.println "Comparing type " + key[i].getClass() + 
						    " and type " + pair[i].getClass(); */	      
						  def count = this.inventoryMap[ p1.text ];
						  this.inventoryMap[ p1.text ] = count ? ++count : 1;
						  doneContrast[i] = 1;
						  /*out.println "Non-match between $p1 and $p2, inventoryMap[$p1] = " +
						    inventoryMap[p1.getText()];*/
						  /*this.out.println "$key/$pair: Found contrast for $p1 and $p2" */
						  /*out.println "meanings[$key] = " + meanings[key.toString()] + 
						    "; meanings[$pair] = " + meanings[pair.toString()]*/
					    }
					}	
				}
			}
			doneContrast = []; /*clear doneContrast for next word*/
		}	
	}

	/* Modification of ghedlund's function with my additions noted */
	IpaTernaryTree buildMinPairs() {
		def wordKeys = this.minPairs.keySet()
		
		wordKeys.each { key ->
			def pairs = this.minPairs.get(key)
			
			wordKeys.each { key2 ->
				if(key == key2) return
				if(key.length() != key2.length()) return /* My addition -rsa */
				/* My addition on line below -rsa */
				if(this.meanings[key.toString()] == this.meanings[key2.toString()]) return 
				if(key.getExtension(LevenshteinDistance).distance(key2) == 1) {
					for (i in 0 .. key.length() - 1) {
						def p1 = key[i]
						def p2 = key2[i]
						/* Find the contrast */
						if (p1.getText() != p2.getText()) {
						 /* If the contrast is on consonant versus consonant
						 then add it. Later we can add similar logic for vowels
						 too */
						  if (PhoneticInventory.isConsonant(p1) &&
						  PhoneticInventory.isConsonant(p2)) {
						  	  pairs.add(key2)
						  }
						}
					}
				}
			}
		}
		
		return minPairs
	}
	
	List getInventory() {
		return this.inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}
}

class ClusterInventory { 
	static private PhonexPattern pattern = 
	PhonexPattern.compile("^\\s<,1>(cluster=\\c\\c+)")
	private Map inventoryMap = [:]
	
	ClusterInventory(records) {
		records.each { record->
			record.IPAActual.each { transcript ->
				/* find two- and three- element word-initial clusters */
				transcript.words().each { word->
					def PhonexMatcher matcher = pattern.matcher(word)
					if (matcher.find()) {
						def clusterTokens = matcher.group(
						  pattern.groupIndex("cluster"))
						def cluster = clusterTokens.join()
						def count = inventoryMap[ cluster ]
						inventoryMap[ cluster ] = count ? ++count : 1
					}
				}
			}
		}
	}
	
	List getInventory() {
		return this.inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}	
}

/* modelPhones refers to the list of phones in a given language, such as
Spanish. modelClusters is the same for clusters. sonorityValues returns a
mapping with all phones mapped to their sonority values in that language. 
PATT() does the PATT analysis and returns treatment targets */
interface Language {
	List getModelPhones()
	List getModelClusters()
	Map getSonorityValues()
	List PATT()
}

/* Since the tables display are language dependent, these functions should
be overwritten depending on the Speaker class implementing */
interface CSVWrite {
	void writeCSV(ClusterInventory clusterInv)
	void writeCSV(PhonemicInventory phonemicInv)
	void writeCSV(PhoneticInventory phoneticInv)
}

abstract class Speaker implements Language, CSVWrite {
/* The "main" class. Should contain all important data structures for analyzing
a client's phonetic, phonemic, and cluster inventories; the steps for 
determining treatment target selection; the inventory "rules" for the specific
language, e.g. allowable phones in English, allowable clusters in Spanish, etc.,
and an interface to output this information. */
	public CSVWriter csv
	protected PrintWriter out
    public PhoneticInventory phoneticInv
  	public PhonemicInventory phonemicInv
  	public ClusterInventory clusterInv
  	public List treatmentTargets
  	
  	/* Language Interface */
  	public abstract List getModelPhones() 
  	public abstract List getModelClusters()
  	public abstract Map getSonorityValues()
  	public abstract List PATT()
  	
  	/* CSVWrite Interface */
  	public void writeCSV(ClusterInventory clusterInv) {
  		this.csv.writeNext("CLUSTER INVENTORY:")
		this.csv.writeNext(clusterInv.inventory.sort{x,y ->
			this.getSonorityDistance(x) <=> this.getSonorityDistance(y)
			} as String[])
		this.csv.writeNext("")
  	}
  	
	abstract public void writeCSV(PhonemicInventory phonemicInv)
	abstract public void writeCSV(PhoneticInventory phoneticInv)
  	
  	public Speaker(records, out, csv) {
  		this.phonemicInv = new PhonemicInventory(records, out)
  		this.phoneticInv = this.phonemicInv.phoneticInv
  		this.clusterInv = new ClusterInventory(records)
  		this.csv = csv
  		this.out = out
  	}
  	

	//Method for converting an IPA string to a string of base phones
	public String getBasePhones(String s) {
		def basePhones = []
		String baseString = ""
		IPATranscript ipa = IPATranscript.parseIPATranscript(s)	
		def IPAPhones = ipa.findAll {it instanceof Phone}
		IPAPhones.each {
			basePhones.add( it.basePhone )
			baseString = baseString + it.basePhone
		}
		return baseString
	}
	
	//Same as above, for a list of IPA strings
	public List getBasePhonesList (List l) {
		basePhonesList = []
		l.each {
			basePhonesList.add(getBasePhones(it))
		}
		return basePhonesList
	}
	
  	public List getClusters() {
  		return this.clusterInv.inventory
  	}

  	public List getOutClusters() {
  		return this.modelClusters - this.modelClusters.intersect(this.clusters)
  	}
  	
  	public List getPhones() {
  		return this.phoneticInv.inventory	
  	}
  	
  	public List getOutPhones() {
  		return this.modelPhones - this.modelPhones.intersect(this.phones)	
  	}
  	
  	public List getPhonemes() {
  		return this.phonemicInv.inventory	
  	}
  	
  	public List getOutPhonemes() {
  		return this.modelPhones - this.modelPhones.intersect(this.phonemes)
	}
  	
  	public Integer getSonorityDistance(String s) {
  		if (this.sonorityValues.containsKey(s[0]) && this.sonorityValues.containsKey(s[1])) {
  			return this.sonorityValues[s[1]] - this.sonorityValues[s[0]]
  		}
  	}
  	
  	public Integer getMinSonorityDistance(List clusters) {
  		def msd = null
  		for (cluster in clusters) {
  			def distance = getSonorityDistance(cluster)
  			if (distance == null) continue
  			if ( msd == null || distance < msd )
				msd = distance
		}
		return msd
  	}
}

class EnglishSpeaker extends Speaker {
	/* Sonority Values Defined.
	Affricates are given a sonority value equal to stops per Parker (2002)
	Could expand this list to provide sonority distance when non-ambient phones occur */
	private static final SAE_PHONES = [ "p":1, "b":2, "t":1, "d":2, 
  	"k":1, "ɡ":2, "f":3, "v":4, "θ":3, "ð":4, "s":3, "z":4, 
  	"ʃ":3, "ʒ":4, "ʧ":1, "ʤ":2, "m":5, "n":5, "ŋ":5, "l":6,
  	"ɹ":6, "w":7, "j":7, "h":7 ]
  
  	private static final SAE_CLUSTERS = [ "tw", "kw", "pj", "kj", "bj", "pɹ", 
  	  "tɹ", "kɹ", "pl", "kl", "bɹ", "dɹ", "ɡɹ", "bl", "ɡl", "fj", "sw", "fɹ", 
  	  "θɹ", "ʃɹ", "fl", "sl", "vj", "mj", "sm", "sn", "sp", "st", "sk", "skw",
  	  "spɹ", "stɹ", "skɹ", "spl" ]  	
	
 	private static final SAE_BASEPHONES = [
		["p","b",null,null,null,null,"t","d",null,null,"ʈ","ɖ","c","ɟ","k","ɡ",
		"q","ɢ",null,null,"ʔ","ʡ"],
		[null,"m",null,"ɱ",null,null,null,"n",null,null,null,"ɳ",null,"ɲ",null,
		"ŋ",null,"ɴ"],
		["ɸ","β","f","v","θ","ð","s","z","ʃ","ʒ","ʂ","ʐ","ç","ʝ","x","ɣ","χ",
		"ʁ","ħ","ʕ","h","ɦ"],
		[null,null,null,null,null,null,"ɬ","ɮ",null,null,"ɕ","ʑ",null,null,
		"ɧ",null,null,null,null,null,"ʜ","ʢ"],
		[null,null,null,null,null,null,"ʦ","ʣ","ʧ","ʤ","ʨ","ʥ","ƛ","λ"],
		[null,null,"ʙ","ⱱ",null,null,"ɾ","r",null,"ɹ","ɽ","ɻ",null,null,
		null,null,null,"ʀ"],
		[null,null,null,null,null,null,"ɺ","l",null,"ɫ",null,"ɭ",null,"ʎ",
		null,"ʟ"],
		["ʍ","w",null,"ʋ",null,null,null,null,null,"ɥ",null,null,null,
		"j",null,"ɰ"]
		]
		
	private static final SAE_PLACE_HEADERS = ["", "bilabial", "", "labiodental",
	"", "interdental", "", "alveolar", "", "palatoalveolar", "", "retroflex", 
	"", "palatal", "", "velar", "", "uvular", "", "pharyngeal", "", "glottal", 
	""]
	
	private static final SAE_MANNER_HEADERS = ["plosive", "nasal", "fricative", 
	"other fricative", "affricate", "approximant", "lateral", "glide"]  
  	
  	public EnglishSpeaker(records, out, csv) {
  		super(records, out, csv)
  	}
  	
  	public Map getSonorityValues() {
		return SAE_PHONES
	} 	
  	  
  	public List getModelPhones() { 
  		return SAE_PHONES.keySet().collect()
  	}  	

	public List getModelClusters() { 
		return SAE_CLUSTERS
	}
	
	public List PATT() {		
		/* Since PATT steps will be conditionally executed, make them closures
		and put them in a loop */
		def methods = [ "Step One":this.&PATTStepOne, 
		  "Step Two":this.&PATTStepTwo, "Step Three":this.&PATTStepThree ]
		
		for (pattStep in methods.keySet()) {
			/* Execute the method as a closure */
			this.treatmentTargets = methods[pattStep]()
			if (this.treatmentTargets) {
				this.out.println("\tTreatment targets found after $pattStep")
				this.csv.writeNext("TARGETS AFTER $pattStep: ")
				this.csv.writeNext(this.treatmentTargets as String[])
				return this.treatmentTargets
			} else {
				this.out.println("\tNo treatment targets found after $pattStep")
				this.csv.writeNext("No targets found after $pattStep") 
			}
		}
		this.csv.writeNext("Error: no targets found!")
		return null
	}
	
	private List PATTStepOne() {
		this.out.println "*************************************************************"
		this.out.println "1. Determine if 3-element /s/CC clusters are appropriate targets"
		
		
		/* Minimum sonority distance and checks for cluster types
		added to Step One to include in every output */
		
		//Examine only 2-element clusters
//Can be revised to search BASE PHONES
		def CCInv = this.clusters.findAll{ it.length() == 2 }
		
		//Determine if speaker produced adjunct clusters
		def msd = this.getMinSonorityDistance(CCInv)
		if (msd == -2) {
				this.out.println "\tSpeaker produced adjunct (-2 sonority distance) cluster(s)."
		}
		
		//Determine if speaker produced glide clusters:
		for (cluster in CCInv) {
			String baseCluster = getBasePhones(cluster)
			if (baseCluster[1] == "j") {
				this.out.println "\tSpeaker produced C[j] cluster(s)"
			}
		}
		
		
		//Use only viable (true) clusters for minimum sonority distance
		def exclude = CCInv.findAll{ this.getSonorityDistance(it) == -2 || 
		["pj", "bj", "fj", "vj", "mj"].contains(it) }
		def msdPool = CCInv - CCInv.intersect( exclude )		

		msd = this.getMinSonorityDistance(msdPool)		
		if (msd == null) {
			this.out.println "\tSpeaker lacks CC clusters to calculate minimum sonority distance"
		}
		else {
			this.out.println "\tMinimum true CC cluster sonority distance: $msd"
		}
		
		def targets = []
		def sCCInv = []
		def sCC = false
		for (cluster in this.clusters ) {
			//stripped to base phones for length and searching
			String baseCluster = getBasePhones(cluster)			
			IPATranscript ipa = IPATranscript.parseIPATranscript(cluster)
			if (baseCluster[0] == (Character) "s" && baseCluster.length() >= 3) { //"s" does not permit any diacritics
				sCC = true
				sCCInv.add( cluster )
			}	
		}

		/* Minimum sonority distance for 3-element clusters */
		//Strip first element from sCC clusters for calculating MSD
		def sCCmsdPool = []
		for (cluster in sCCInv) {
			sCCmsdPool.add( cluster.substring(1) )
		}
		if (sCC) {
			this.out.println "\tSpeaker produced 3-element cluster(s)"
			msd = this.getMinSonorityDistance(sCCmsdPool)
			this.out.println "\tSpeaker minimum [s]CC cluster sonority distance: $msd"
			return []
		}
		else println "\tSpeaker does not produce [s]CC clusters"
		
		
		/* if "kw", "pr", "tr", "kr", or "pl" can be constructed from phonemes
		in the phonemic inventory, prepend /s/ to it and return it as a target.
		QUESTION: count base phones or not? */
		def allowedClusters = ["kw", "pɹ", "tɹ", "kɹ", "pl"]
		def c2s = this.phonemes.findAll{ ["p", "t", "k"].contains(it) }
		def c3s = this.phonemes.findAll{ ["w", "l", "ɹ"].contains(it) }
		for (c2 in c2s ) {
			for (c3 in c3s) {
				if ( allowedClusters.contains(c2 + c3) ) {
					targets.add("s"+c2+c3)
				}
			}
		}
		if ( targets.size() == 0 ) {
			this.out.println "\tSpeaker's phonemic inventory does not permit 3-element cluster targets"
		}
		return targets	
	}

	private List PATTStepTwo() {
		this.out.println "\t***************************************************"
		this.out.println "2. Determine if 2-element CC clusters are appropriate targets"

		def targetPool = this.modelClusters.findAll{it.length() == 2}
		//this.out.println "PATTStepTwo: targetPool: " + targetPool
				
		/* a. Remove IN clusters */
		//this.out.println "PATTStepTwo: Removing IN clusters..."
		targetPool = targetPool - targetPool.intersect( this.clusters )
		//this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) {
			this.out.println "\tSpeaker already produced all potential CC cluster targets."
			return null
		}		
		
		/* b. Remove clusters with SD=-2 and /C/j clusters 
		/*Adjunct and glide clusters are skipped due to inconsistent generalization in research*/
		this.out.println "\tAdjunct and C[j] cluster targets may not\n\t\tbe as effective targets and have been omitted."
		this.out.println "\t\t(Barlow, 2001; Gierut, 1999; Smit, 1993;\n\t\tSmit, Hand, Freilinger, Bernthal, & Bird, 1990)."	
		
		def removables = targetPool.findAll{ this.getSonorityDistance(it) == -2 || 
		["pj", "bj", "fj", "vj", "mj"].contains(it) }
		//this.out.println "PATTStepTwo: removables: $removables"
		targetPool = targetPool - targetPool.intersect( removables )
		//this.out.println "PATTStepTwo: Removed /C/j clusters and SD=-2 clusters"
		//this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) {
			this.out.println "\tOnly adjunct or glide CC clusters available."
			return null
		}
		/* c. Get minimum sonority distance (MSD) and remove all clusters with MSD
		greater than or equal to that sonority distance */
		def clusters = this.clusters.findAll{ it.length() == 2 }
		/* If there aren't any clusters in the inventory, set sonority distance to
		a number beyond the max. This is a hack that needs to be fixed */
		def msd = this.getMinSonorityDistance(clusters)
		if (msd == null) {
			msd = 10
			this.out.println "\tSpeaker produced no clusters."
		}
		removables = targetPool.findAll{ this.getSonorityDistance(it) >= msd }
		//this.out.println "PATTStepTwo: removables: $removables"
		targetPool = targetPool - targetPool.intersect( removables )
		//this.out.println "PATTStepTwo: Removed clusters with msd >= $msd"
		//this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) {
			this.out.println "\tNo appropriate cluster targets with sonority distance\n\t\tlower than speaker's minimum"
			return null
		}

		/* d. If the pool contains /sw, sl, sm, or sn/ determine the error pattern
  in these clusters, e.g. /sn/->/s/ or /sn/->n. If error patterns are similar to
  /sp, st, sk/, remove the clusters. If they are different, keep them in pool. 
  If it is unclear, remove the clusters. Right now we are just outputting what
  the potential 2-element 's' clusters are, so this remains to be implemented. 
  */
		removables = targetPool.findAll{ ["sw", "sl", "sm", "sn"].contains(it) }
		targetPool = targetPool - targetPool.intersect( removables )
		if (removables) {
			/* This is where the potential 2-element clusters can be added
			to the CSV file */
			this.out.println "\t[" + removables.join(",") + "] could be appropriate targets, although\n\t\tgeneralization may be less predictable (Gierut, 1999)."
			//May want to move this to another place in the printout and edit.
			//this.csv.writeNext("Potential Cluster Targets after Step Two: ")
			//this.csv.writeNext(removables as String[])
		}
		//this.out.println "PATTStepTwo: Removed sw, sl, sm, sn for now"
		//this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
			
		/* e. Find smallest sonority distance in targetPool. If there is more
	than one, find those with two OUT phones and return them as a treatment
	target list. */
		msd = this.getMinSonorityDistance(targetPool)

		if (msd == null) {
			this.out.println "\tSpeaker produced no viable clusters."
		}
			
		removables = targetPool.findAll{ this.getSonorityDistance(it) > msd }
		targetPool = targetPool - targetPool.intersect( removables )
		//this.out.println "PATTStepTwo: Removed all from target pool >= $msd"
		//this.out.println "PATTStepTwo: targetPool: " + targetPool
		def targets = []
		for (cluster in targetPool) {		
			// Add clusters from pool for which both segments are OUT phones
			// Modification can be made here to list more potential targets
			if (this.outPhones.contains(cluster[0]) && this.outPhones.contains(cluster[1])) {
			    targets.add(cluster)
			}
		}
		return targets
	}	

	private List PATTStepThree() {
		this.out.println "\t***************************************************"
		this.out.println "3. Select a singleton target"
		
		def targetPool = this.outPhones
		/* First, remove all stimulable sounds: since we haven't implemented 
		stimulable sounds yet, skipping this...*/
		this.out.println "\tAlthough recommended, AutoPATT does not consider\n\t\tstimulability for singleton target selection."
		
		/* Cross out early acquired sounds. For english this is [p, b, t, d, k, g,
		f, v, m, n, ŋ, w, j, h] */
		def earlySounds = ["p", "b", "t", "d", "k", "ɡ", "f", "v", "m", "n", "ŋ", "w",
					   "j", "h"]
		targetPool = targetPool - targetPool.intersect( earlySounds )
		
		/* From the list of common sounds sorted from most to least, pick the first
		one we see from the targetPool 
		NOTE: need to change from hard coding to account for other languages */
		def commonSounds = ["ɹ", "l", "s", "z", "ð", "θ", "ʃ", "ʤ", "ʧ", "ʒ"]
		def targets = []
		for (sound in commonSounds)
			if (targetPool.contains(sound)) targets.add(sound)
		
		return targets
	}

	public void writeCSV(PhoneticInventory phoneticInv) {
		/* Algorithm: 
		1) Have pre-created 8x22 IPA list of consonants, all values set to false
		and pre-created map which will store key:values of all variants of the 
		basePhone, e.g. basePhoneMap["t":["tʷ","t","t̪"]]
		2) Go through each phone in phonetic inventory, convert to IPATranscript
		3) get Phone from IPATranscript.
		4) get basePhone from Phone.
		5) if basePhone VALUE is null, create list, otherwise append inventory
		phone to existing list.
		6) for each row in map, filter out and print all true values.*/
		def basePhoneMap = [:]
		
		phoneticInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : this.SAE_BASEPHONES) {
		  	  for (String item : row) {
		  	  	  if ( !(ipa[0] instanceof CompoundPhone) && 
		  	  	  (Character) item == ipa[0].basePhone) {
		  	  	  	  if (basePhoneMap[item])
		  	  	  	  	  basePhoneMap[item].add(ipa[0])
		  	  	  	  else 
		  	  	  	  	  basePhoneMap[item] = [ ipa[0] ]
		  	  	  	  		  	  	  	  
		  	  	  	  break mainLoop
		  	  	  }
		  	  }
		  }
		}
		
		this.csv.writeNext("PHONETIC INVENTORY:")
		this.csv.writeNext(this.SAE_PLACE_HEADERS as String[])
		def csvRow = []
		
		this.SAE_BASEPHONES.eachWithIndex { row, i ->
			csvRow.add(this.SAE_MANNER_HEADERS[i])
			row.each  {
				if (basePhoneMap[it]) {
					csvRow.add(basePhoneMap[it].join(","))
				} else {
					csvRow.add(" ")
				}
			}
			this.csv.writeNext(csvRow as String[])
			csvRow = []
		}
		this.csv.writeNext("")
	}
	
	public void writeCSV(PhonemicInventory phonemicInv) {		
		def wordKeys = phonemicInv.minPairs.keySet();
		this.csv.writeNext("Minimal Pairs:")
		wordKeys.each { key ->
			def pairs = phonemicInv.minPairs.get(key) as Queue;
			
			if(pairs.size() > 0) {
				pairs.offerFirst(key)
				this.csv.writeNext(pairs as String[])
			}
		}
		this.csv.writeNext("")
		
		def basePhoneMap = [:]
		
		phonemicInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : this.SAE_BASEPHONES) {
		  	  for (String item : row) {
		  	  	  if ((Character) item == ipa[0].basePhone) {
		  	  	  	  if (basePhoneMap[item])
		  	  	  	  	  basePhoneMap[item].add(ipa[0])
		  	  	  	  else 
		  	  	  	  	  basePhoneMap[item] = [ ipa[0] ]
		  	  	  	  		  	  	  	  
		  	  	  	  break mainLoop
		  	  	  }
		  	  }
		  }
		}
		
		this.csv.writeNext("PHONEMIC INVENTORY:")
		this.csv.writeNext(this.SAE_PLACE_HEADERS as String[])
		def csvRow = []
		
		this.SAE_BASEPHONES.eachWithIndex { row, i ->
			csvRow.add(this.SAE_MANNER_HEADERS[i])
			row.each  {
				if (basePhoneMap[it]) {
					csvRow.add(basePhoneMap[it].join(","))
				} else {
					csvRow.add(" ")
				}
			}
			this.csv.writeNext(csvRow as String[])
			csvRow = []
		}
		this.csv.writeNext("")
	}
	
}

class SpanishSpeaker extends Speaker {
	//Sonority Values Defined.
	//Affricates are given a sonority value equal to stops per Parker (2002)
	//Could expand this list to provide sonority distance when non-ambient phones occur.		
	private static final ESP_PHONES = [ "p":1, "b":2, "t":1, "d":2, 
  	"k":1, "ɡ":2, "f":3, "s":3, "x":3, "ʧ":1, "m":5, "n":5, "ɲ":5, "r":6,
  	"ɾ":6, "w":7, "β":2, "ð":2, "l":6, "j":7, "ɣ":2 ]
	
  	private static final ESP_CLUSTERS = [ "pw", "tw", "kw", "pj", "tj", "kj", 
  	  "bw", "dw", "ɡw", "bj", "dj", "ɡj", "pɾ", "tɾ", "kɾ", "pl", "kl", "fw",
  	  "fj", "sw", "sj", "bɾ", "dɾ", "ɡɾ", "bl", "ɡl", "fɾ", "fl", "mw", "mj",
  	  "nw", "nj", "lw", "lj", "rw", "rj" ]
  	
  	private static final ESP_WJ_CLUSTERS = ["pw", "tw", "kw", "pj", "tj", "kj", 
  	  "bw", "dw", "ɡw", "bj", "dj", "ɡj", "fw", "fj", "sw", "sj", "mw", "mj",
  	  "nw", "nj", "rw", "rj" ]
  	  
  	private static final ESP_LR_CLUSTERS = [ "pɾ", "tɾ", "kɾ", "pl", "kl", "bɾ",
  	"dɾ", "ɡɾ", "bl", "ɡl", "fɾ", "fl" ]	
  	
  	private static final ESP_BASEPHONES = [
		["p","b",null,null,null,null,"t","d",null,null,"ʈ","ɖ","c","ɟ","k","ɡ",
		"q","ɢ",null,null,"ʔ","ʡ"],
		[null,"m",null,"ɱ",null,null,null,"n",null,null,null,"ɳ",null,"ɲ",null,
		"ŋ",null,"ɴ"],
		["ɸ",null,"f","v","θ",null,"s","z","ʃ","ʒ","ʂ","ʐ","ç","ʝ","x",null,"χ",
		"ʁ","ħ","ʕ","h","ɦ"],
		[null,null,null,null,null,null,"ɬ","ɮ",null,null,"ɕ","ʑ",null,null,
		"ɧ",null,null,null,null,null,"ʜ","ʢ"],
		[null,null,null,null,null,null,"ʦ","ʣ","ʧ","ʤ","ʨ","ʥ","ƛ","λ"],
		["w","β","ʙ","ⱱ",null,"ð","ɺ","l","ɹ","ɽ","ɻ",null,null,
		null,"ɣ",null,"ʀ"],
		[null,null,null,null,null,null,"ɾ","r",null,"ɫ",null,"ɭ",null,"ʎ",
		null,"ʟ"],
		["ʍ",null,null,"ʋ",null,null,null,null,null,"ɥ",null,null,null,
		"j",null,"ɰ"]
	]
		
	private static final ESP_PLACE_HEADERS = ["", "bilabial", "", "labiodental", "", "dental", "", 
		"alveolar", "", "palatoalveolar", "", "retroflex", "", "palatal", "", 
		"velar", "", "uvular", "", "pharyngeal", "", "glottal", ""]
	
	private static final ESP_MANNER_HEADERS = ["plosive", "nasal", "fricative", "other fricative",
		"affricate", "approximant", "rhotic", "glide"]
  	
  	public SpanishSpeaker(records, out, csv) {
  		super(records, out, csv)
  	}
  	
  	public List getModelPhones() { 
		return ESP_PHONES.keySet().collect()
	}
	
	public Map getSonorityValues() {
		return ESP_PHONES
	}
	
	public List getModelClusters() { 
		return ESP_CLUSTERS
	}
	
	public List getModelWJClusters() {
  		return ESP_WJ_CLUSTERS
  	}
  	
  	public List getModelLRClusters() {
  		return ESP_LR_CLUSTERS
  	}
	
	public List PATT() {
		def methods = [ "Step One":this.&PATTStepOne, 
		  "Step Two":this.&PATTStepTwo ]
		
		for (pattStep in methods.keySet()) {
			this.treatmentTargets = methods[pattStep]()
			if (this.treatmentTargets) {
				this.out.println("\tTreatment targets found after $pattStep")
				this.csv.writeNext("TARGET(S) AFTER $pattStep: ")
				this.csv.writeNext(this.treatmentTargets as String[])	
				return this.treatmentTargets
			} else {
				this.out.println("\tNo treatment targets found after $pattStep")
				this.csv.writeNext("No targets found after $pattStep") 
			}
		}
		this.csv.writeNext("Error: no targets found!")
		return null	
	}
	
	private List PATTStepOne() {		
		this.out.println "*************************************************************"
		this.out.println "1. Determine if 2-element CC clusters are appropriate targets"
		
		/* Algorithm: you'll have two separate cluster lists. One for C+/w,j/ 
		clusters, and one for C+/l,r/ clusters. This might involve modifying the
		cluster inventory for Spanish.*/
		def WJClusters = this.clusters.intersect( this.modelWJClusters )
		def LRClusters = this.clusters.intersect( this.modelLRClusters )
		def WJTargetPool = this.modelWJClusters
		def LRTargetPool = this.modelLRClusters
		/* a. cross out all IN clusters from both charts. If both empty go to 
		Step 2. */
		WJTargetPool = WJTargetPool - WJTargetPool.intersect(WJClusters)
		LRTargetPool = LRTargetPool - LRTargetPool.intersect(LRClusters)
		if (!(WJTargetPool || LRTargetPool)) {
			this.out.println "\tSpeaker already produced all potential CC cluster targets."
			return null
		}
		/* b. Get MSD for /w,j/ clusters. Cross out all clusters >= MSD.*/
		if (WJTargetPool) {
			this.out.println "\t** Examining C+[w,j] clusters **"
			def msd = this.getMinSonorityDistance(WJClusters)
			if (msd == null) {
				msd = 10
				this.out.println "\tSpeaker produced no C+Glide clusters. Minimum sonority distance not calculated."
			}
			else {
				this.out.println "\tSpeaker's minimum C+Glide cluster sonority distance: $msd"
			}			
			
			def removables = WJTargetPool.findAll{ this.getSonorityDistance(it) >= msd }
			WJTargetPool = WJTargetPool - WJTargetPool.intersect( removables )
		}
	   
		/*c-1. Get MSD for /l,r/ clusters. Cross out all clusters >= MSD. */
		if (LRTargetPool) {
			this.out.println "\t** Examining C+[l,ɾ] clusters **"
			def msd = this.getMinSonorityDistance(LRClusters)
			if (msd == null) {
				msd = 10
				this.out.println "\tSpeaker produced no C+Liquid clusters. Minimum sonority distance not calculated."
			}
			else {
				this.out.println "\tSpeaker's minimum C+Liquid cluster sonority distance: $msd"
			}	
			def removables = LRTargetPool.findAll{ this.getSonorityDistance(it) >= msd }
			LRTargetPool = LRTargetPool - LRTargetPool.intersect( removables )
		}
		
		/* c-2. If pool empty, go to Step 2. */
		if (!(WJTargetPool || LRTargetPool)) {
			this.out.println "\tNo appropriate cluster targets with sonority distance\n\t\tlower than speaker's minimum."
			return null
		}
		/*d. Choose clusters with smallest sonority distance. If more than one is 
		chosen, select one that includes OUT phones. If there are more than one,
		consider selecting one of each type from /w,j/ and /l,r/. Those will be 
		your treatment targets. Return targets */
		def targets = []
		//Get smallest sonority distance for C+/w,j/ targets and C+/l,r/ targets
		//separately and add them to targets list.
		def msd = this.getMinSonorityDistance(WJTargetPool)
		def WJTargets = WJTargetPool.findAll{ this.getSonorityDistance(it) == msd }
		msd = this.getMinSonorityDistance(LRTargetPool)
		def LRTargets = LRTargetPool.findAll{ this.getSonorityDistance(it) == msd }
		targets = WJTargets + LRTargets
		
		return targets
		
	}
	
	private List PATTStepTwo() {
		this.out.println "\t***************************************************"
		this.out.println "3. Select a singleton target"		
		
		/* Algorithm:
		1. Get all out phones.
		2. Cross out all stimulable sounds
		3. Cross out early acquired sounds [p t k m n ñ l j x]
		4. Circle those with greatest complexity.
		5. If multiple sounds, select one that occurs most frequently.
		In Spanish this is [s l n t edh r t m B velar f z j r x tS ng] 
		6. Return singleton target */
		def targetPool = this.outPhones
		
		/* Cross out early acquired sounds. For Spanish this is [p, t, k, m, n, 
		ñ, l, j, x] */
		def earlySounds = ["p", "t", "k", "m", "n", "ɲ", "l", "j", "x"]
		targetPool = targetPool - targetPool.intersect( earlySounds )
		
		/* From the list of common sounds sorted from most to least, pick the first
		one we see from the targetPool 
		NOTE: need to change from hard coding to account for other languages */
		def commonSounds = ["s", "l", "n", "t", "ð", "ɾ", "m", "p", "β", "ɣ", "f", "z",
						"j", "r", "x", "ʧ", "ɲ"]
		def targets = []
		for (sound in commonSounds)
			if (targetPool.contains(sound)) targets.add(sound)
		
		return targets
	}
	
	public void writeCSV(PhonemicInventory phonemicInv) {
		def wordKeys = phonemicInv.minPairs.keySet();
		csv.writeNext("Minimal Pairs:")
		wordKeys.each { key ->
			def pairs = phonemicInv.minPairs.get(key) as Queue;
			
			if(pairs.size() > 0) {
				pairs.offerFirst(key)
				this.csv.writeNext(pairs as String[])
			}
		}
		this.csv.writeNext("")
		
		def basePhoneMap = [:]
		
		phonemicInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : this.ESP_BASEPHONES) {
		  	  for (String item : row) {
		  	  	  if ((Character) item == ipa[0].basePhone) {
		  	  	  	  if (basePhoneMap[item])
		  	  	  	  	  basePhoneMap[item].add(ipa[0])
		  	  	  	  else 
		  	  	  	  	  basePhoneMap[item] = [ ipa[0] ]
		  	  	  	  		  	  	  	  
		  	  	  	  break mainLoop
		  	  	  }
		  	  }
		  }
		}
		
		this.csv.writeNext("PHONEMIC INVENTORY:")
		this.csv.writeNext(this.ESP_PLACE_HEADERS as String[])
		def csvRow = []
		
		this.ESP_BASEPHONES.eachWithIndex { row, i ->
			csvRow.add(this.ESP_MANNER_HEADERS[i])
			row.each  {
				if (basePhoneMap[it]) {
					csvRow.add(basePhoneMap[it].join(","))
				} else {
					csvRow.add(" ")
				}
			}
			this.csv.writeNext(csvRow as String[])
			csvRow = []
		}
		this.csv.writeNext("")	
	}
	
	public void writeCSV(PhoneticInventory phoneticInv) {
		def basePhoneMap = [:]
		
		phoneticInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : this.ESP_BASEPHONES) {
		  	  for (String item : row) {
		  	  	  if ( !(ipa[0] instanceof CompoundPhone) && 
		  	  	  (Character) item == ipa[0].basePhone) {
		  	  	  	  if (basePhoneMap[item])
		  	  	  	  	  basePhoneMap[item].add(ipa[0])
		  	  	  	  else 
		  	  	  	  	  basePhoneMap[item] = [ ipa[0] ]
		  	  	  	  		  	  	  	  
		  	  	  	  break mainLoop
		  	  	  }
		  	  }
		  }
		}
		
		this.csv.writeNext("PHONETIC INVENTORY:")
		this.csv.writeNext(this.ESP_PLACE_HEADERS as String[])
		def csvRow = []
		
		this.ESP_BASEPHONES.eachWithIndex { row, i ->
			csvRow.add(this.ESP_MANNER_HEADERS[i])
			row.each  {
				if (basePhoneMap[it]) {
					csvRow.add(basePhoneMap[it].join(","))
				} else {
					csvRow.add(" ")
				}
			}
			this.csv.writeNext(csvRow as String[])
			csvRow = []
		}
		this.csv.writeNext("")	
	}
}

def project = window.project
if (project == null) return

/* Prepare CSV file */
fd = new FileDialog(new Frame(), "Please specify a CSV file to output data", FileDialog.SAVE)
fd.setVisible(true)
filename = fd.getFile()
if (filename == null) {
  JOptionPane.showMessageDialog(null, "You must choose a valid file to output data",
    "PATT", JOptionPane.WARNING_MESSAGE)
  return
} else {
  if (!filename.toLowerCase().endsWith(".csv"))
    filename += ".csv"
  filename = fd.getDirectory() + filename
  csv = new CSVWriter(new FileWriter(filename))
} 

/* Allow user to select sessions for analysis */
def sessionSelector = new SessionSelector(project)
def scroller = new JScrollPane(sessionSelector)
JOptionPane.showMessageDialog(window, scroller, "Select Sessions", JOptionPane.INFORMATION_MESSAGE)
	
def sessions = sessionSelector.selectedSessions;
if(sessions.size() == 0) return

/* Establish sessions and records for analysis */
records = []
sessions.each { sessionLoc ->
	session = project.openSession(sessionLoc.corpus, sessionLoc.session)
	count = session.getRecordCount()
	println "Session: $sessionLoc \n\t $count records"
	
	/* Add session and record information to output */
	csv.writeNext(["Session", "Corpus", "Records"] as String[])
	csv.writeNext(["$sessionLoc.session", "$sessionLoc.corpus", "$count"] as String[])
	csv.writeNext("")
	
	records += session.records
}

/* Allow user to choose language to analyze */
def langComboMap = [ "English":EnglishSpeaker, "Spanish":SpanishSpeaker]
userSelection = JOptionPane.showInputDialog(
  null, "Choose client's language to analyze:", "Choose Language", 
  JOptionPane.PLAIN_MESSAGE, null, langComboMap.keySet() as Object[],
  "English")
if (!userSelection) return
else speaker = langComboMap[userSelection].newInstance(records,
  getBinding().out, csv)

/* Add language, version number, and date of analysis to shell output */
println "Language: $userSelection"
println "AutoPATT version $AutoPATTversion"
def date = new Date()
def sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
println "Analysis date: " + sdf.format(date)
println ""
println "Phonological Assessment and Treatment Target Selection (PATT)"
println "*************************************************************"
println "Target selection occurs in a step-by-step fashion based on\nthe results of the individual speaker’s overall assessment\n(Barlow, Taps, & Storkel, 2010)."
/* Write initial analysis header to csv output */
csv.writeNext("AutoPATT version $AutoPATTversion")
csv.writeNext("Language: $userSelection")
csv.writeNext("Analysis date:")
csv.writeNext(sdf.format(date))
csv.writeNext("")
speaker.writeCSV(speaker.phoneticInv)
speaker.writeCSV(speaker.phonemicInv)
speaker.writeCSV(speaker.clusterInv)
speaker.PATT()
println "*************************************************************"
println "RECOMMENDED TREATMENT TARGET(S): " + speaker.treatmentTargets
println "Phones to monitor: " + speaker.outPhones
csv.writeNext("Phones to monitor:")
csv.writeNext(speaker.outPhones as String[] )
println "Phonemes to monitor: " + speaker.outPhonemes
csv.writeNext("Phonemes to monitor:")
csv.writeNext(speaker.outPhonemes as String[])
println "Clusters to monitor: " + speaker.outClusters
csv.writeNext("Clusters to monitor:")
csv.writeNext(speaker.outClusters as String[])
println "\nPlease see output in " + filename
println "For more detailed information about AutoPATT and PATT, visit https://slhs.sdsu.edu/phont/the-patt/"

//DEBUG PRINTOUT

csv.close()
