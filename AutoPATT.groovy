import au.com.bytecode.opencsv.CSVWriter;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter

import ca.phon.ipa.*;
import ca.phon.ipa.parser.*;
import ca.phon.app.session.*;
import ca.phon.ipa.features.CompoundFeatureComparator;
import ca.phon.ipa.features.FeatureComparator;
import ca.phon.ipa.tree.*;
import ca.phon.phonex.*;

class PhoneticInventory { 
	static private IPATokens ipaTokens = new IPATokens()
	private Map inventoryMap
	
	/* create a PhoneticInventory from Phon records */
	PhoneticInventory(records) {
		inventoryMap = [:]
		records.each{ record ->
			record.IPAActual.each { transcript ->
				transcript.findAll { it instanceof Phone}.each {
					/* For a compound tesh, getBasePhone() outputted an 'x' */
					//out.println "$it - " + ipaTokens.getTokenType( it.getBasePhone() )
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
		return inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
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

	PhonemicInventory(records, out) {
		this.inventoryMap = [:]
		this.meanings = [:]
		this.phoneticInv = new PhoneticInventory(records)
		this.minPairs = new IpaTernaryTree( 
		  new CompoundFeatureComparator(
		    FeatureComparator.createPlaceComparator()))
		
		/* First initialize minPair HashSets and populate meanings Map */
		records.each { record ->
			record.orthography.eachWithIndex { tier, index ->
				def words = tier.toString().tokenize()
				/* If there are more orthos than productions...*/
				if (words.count{it} != record.IPAActual[index].words().count{it})
					out.println "Orthography <-> IPA Actual Count Mismatch!"
				
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
						/* if the consonants don't match we should have a 
						minimal pair with a consonant contrast here */
						if ( p1.text != p2.text && !(doneContrast[i])) {  
						  /* out.println "Comparing type " + key[i].getClass() + 
						    " and type " + pair[i].getClass(); */	      
						  def count = this.inventoryMap[ p1.text ];
						  this.inventoryMap[ p1.text ] = count ? ++count : 1;
						  doneContrast[i] = 1;
						  /*out.println "Non-match between $p1 and $p2, inventoryMap[$p1] = " +
						    inventoryMap[p1.getText()];*/
						  this.out.println "$key/$pair: Found contrast for $p1 and $p2"
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
		def wordKeys = minPairs.keySet()
		
		wordKeys.each { key ->
			def pairs = minPairs.get(key)
			
			wordKeys.each { key2 ->
				if(key == key2) return
				if(key.length() != key2.length()) return /* My addition -rsa */
				/* My addition on line below -rsa */
				if(meanings[key.toString()] == meanings[key2.toString()]) return 
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
		return inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}
}

class ClusterInventory { 
	static private PhonexPattern pattern = 
	PhonexPattern.compile("^\\s<,1>(cluster=\\c\\c+)")
	private Map inventoryMap = [:]
	
	ClusterInventory(records, out) {
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
		return inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}	
}

interface Language {
	List getModelPhones()
	List getModelClusters()
	Map getSonorityValues()
	List PATT()
}

interface CSVWrite {
	void writeCSV(ClusterInventory clusterInv)
	void writeCSV(PhonemicInventory phonemicInv)
	void writeCSV(PhoneticInventory phoneticInv)
}

abstract class Speaker implements Language, CSVWrite {
/* Should contain all important data structures for analyzing
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
  		this.clusterInv = new ClusterInventory(records, out)
  		this.csv = csv
  		this.out = out
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
	private static final SAE_PHONES = [ "p":1, "b":2, "t":1, "d":2, 
  	"k":1, "ɡ":2, "f":5, "v":6, "θ":5, "ð":6, "s":5, "z":6, 
  	"ʃ":5, "ʒ":6, "ʧ":3, "ʤ":4, "m":7, "n":7, "ŋ":7, "l":8,
  	"ɹ":8, "w":9, "j":9, "h":9 ]
  
  	private static final SAE_CLUSTERS = [ "tw", "kw", "pj", "kj", "bj", "pɹ", 
  	  "tɹ", "kɹ", "pl", "kl", "bɹ", "dɹ", "ɡɹ", "bl", "ɡl", "fj", "sw", "fɹ", 
  	  "θɹ", "ʃɹ", "fl", "sl", "vj", "mj", "sm", "sn", "sp", "st", "sk", "skw",
  	  "spɹ", "stɹ", "skɹ", "spl" ]  	
	
 	
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
		def methods = [ "Step One":this.&PATTStepOne, 
		  "Step Two":this.&PATTStepTwo, "Step Three":this.&PATTStepThree ]
		
		for (pattStep in methods.keySet()) {
			this.treatmentTargets = methods[pattStep]()
			if (this.treatmentTargets) {
				this.out.println("Treatment targets found after $pattStep")
				this.csv.writeNext("Targets after $pattStep: ")
				this.csv.writeNext(this.treatmentTargets as String[])
				return this.treatmentTargets
			} else {
				this.out.println("No treatment targets found after $pattStep")
				this.csv.writeNext("No treatment targets found after $pattStep") 
			}
		}
		this.csv.writeNext("Error: no targets found!")
		return null
	}
	
	private List PATTStepOne() {
		def targets = []
		for (cluster in this.clusters ) {
			IPATranscript ipa = IPATranscript.parseIPATranscript(cluster)
			if (ipa[0].basePhone == (Character) "s" && cluster.length() >= 3) {
				this.out.println "Found /s/ cluster"
				return [];
			}
		}
		
		/* if "kw", "pr", "tr", "kr", or "pl" can be constructed from phonemes
		in the phonemic inventory, prepend /s/ to it and return it as a target.
		QUESTION: count base phones or not? */
		def allowedClusters = ["kw", "pɹ", "tɹ", "kɹ", "pl"]
		def c2s = this.phonemes.findAll{ ["p", "t", "k"].contains(it) }
		def c3s = this.phonemes.findAll{ ["w", "l", "ɹ"].contains(it) }
		for (c2 in c2s ) {
			for (c3 in c3s) {
				if ( allowedClusters.contains(c2 + c3) ) {
					this.out.println "Looking at $c2 and $c3"
					targets.add("s"+c2+c3)
				}
			}
		}
		return targets	
	}

	private List PATTStepTwo() {
		def targetPool = this.modelClusters.findAll{it.length() == 2}
		this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		/* Remove IN clusters */
		this.out.println "PATTStepTwo: Removing IN clusters..."
		targetPool = targetPool - targetPool.intersect( this.clusters )
		this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
		
		/* Get minimum sonority distance (MSD) and remove all clusters with MSD
		greater than or equal to that sonority distance */
		def clusters = this.clusters.findAll{ it.length() == 2 }
		/* If there aren't any clusters in the inventory, set sonority distance to
		a number beyond the max. This is a hack that needs to be fixed */
		def msd = this.getMinSonorityDistance(clusters)
		if (msd == null) msd = 10
		this.out.println "PATTStepTwo: msd: $msd"
		def removables = targetPool.findAll{ this.getSonorityDistance(it) >= msd }
		this.out.println "PATTStepTwo: removables: $removables"
		targetPool = targetPool - targetPool.intersect( removables )
		this.out.println "PATTStepTwo: Removed clusters with msd >= $msd"
		this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
		
		/* Remove clusters with SD=-2 and /C/j clusters 
		   Note: due to changes in sonority, SD=-2 is now SD=-4*/
		removables = targetPool.findAll{ this.getSonorityDistance(it) == -4 || 
		["pj", "bj", "fj", "vj", "mj"].contains(it) }
		this.out.println "PATTStepTwo: removables: $removables"
		targetPool = targetPool - targetPool.intersect( removables )
		this.out.println "PATTStepTwo: Removed /C/j clusters and SD=-2 clusters"
		this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
			
		/* If the pool contains /sw, sl, sm, or sn/ determine the error pattern
  in these clusters, e.g. /sn/->/s/ or /sn/->n. If error patterns are similar to
  /sp, st, sk/, remove the clusters. If they are different, keep them in pool. 
  If it is unclear, remove the clusters. Right now we are just outputting what
  the potential 2-element 's' clusters are */
		removables = targetPool.findAll{ ["sw", "sl", "sm", "sn"].contains(it) }
		targetPool = targetPool - targetPool.intersect( removables )
		if (removables) {
			/* This is where the potential 2-element clusters can be added
			to the CSV file */
			this.out.println "PATTStepTwo: Consider " + removables.join(",")
			this.csv.writeNext("Potential Cluster Targets after Step Two: ")
			this.csv.writeNext(removables as String[])
		}
		this.out.println "PATTStepTwo: Removed sw, sl, sm, sn for now"
		this.out.println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
			
		/* Find smallest sonority distance in targetPool. If there is more
	than one, find those with an OUT phone and return them as a treatment
	target list. */
		msd = this.getMinSonorityDistance(targetPool)
		removables = targetPool.findAll{ this.getSonorityDistance(it) > msd }
		targetPool = targetPool - targetPool.intersect( removables )
		this.out.println "PATTStepTwo: Removed all from target pool >= $msd"
		this.out.println "PATTStepTwo: targetPool: " + targetPool
		def targets = []
		for (cluster in targetPool) {
			for (phone in cluster)
			  if (this.outPhones.contains(phone)) {
				  targets.add(cluster)
				  break
			  }
		}
		return targets
	}	

	private List PATTStepThree() {
		def targetPool = this.outPhones
		
		/* First, remove all stimulable sounds: since we haven't implemented 
		stimulable sounds yet, skipping this...*/
		
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

		def basePhones = [
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
		def placeHeaders = ["", "bilabial", "", "labiodental", "", "interdental", "", 
		"alveolar", "", "palatoalveolar", "", "retroflex", "", "palatal", "", 
		"velar", "", "uvular", "", "pharyngeal", "", "glottal", ""]
		def mannerHeaders = ["plosive", "nasal", "fricative", "other fricative",
		"affricate", "approximant", "lateral", "glide"]
	
		def basePhoneMap = [:]
		
		phoneticInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : basePhones) {
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
		this.csv.writeNext(placeHeaders as String[])
		def csvRow = []
		
		basePhones.eachWithIndex { row, i ->
			csvRow.add(mannerHeaders[i])
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
		
		def basePhones = [
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
		def placeHeaders = ["", "bilabial", "", "labiodental", "", "interdental", "", 
		"alveolar", "", "palatoalveolar", "", "retroflex", "", "palatal", "", 
		"velar", "", "uvular", "", "pharyngeal", "", "glottal", ""]
		def mannerHeaders = ["plosive", "nasal", "fricative", "other fricative",
		"affricate", "approximant", "lateral", "glide"]
		
		def basePhoneMap = [:]
		
		phonemicInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : basePhones) {
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
		this.csv.writeNext(placeHeaders as String[])
		def csvRow = []
		
		basePhones.eachWithIndex { row, i ->
			csvRow.add(mannerHeaders[i])
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
	private static final ESP_PHONES = [ "p":1, "b":2, "t":1, "d":2, 
  	"k":1, "ɡ":2, "f":3, "s":3, "x":3, "ʧ":4, "m":5, "n":5, "ɲ":5, "r":6,
  	"ɾ":6, "w":7, "β":7, "ð":7, "l":7, "j":7, "ɣ":7 ]
	
  	private static final ESP_CLUSTERS = [ "pw", "tw", "kw", "pj", "tj", "kj", 
  	  "bw", "dw", "ɡw", "bj", "dj", "ɡj", "pɾ", "tɾ", "kɾ", "pl", "kl", "fw",
  	  "fj", "sw", "sj", "bɾ", "dɾ", "ɡɾ", "bl", "ɡl", "fɾ", "fl", "mw", "mj",
  	  "nw", "nj", "lw", "lj", "rw", "rj" ]
  	
  	private static final ESP_WJ_CLUSTERS = ["pw", "tw", "kw", "pj", "tj", "kj", 
  	  "bw", "dw", "ɡw", "bj", "dj", "ɡj", "fw", "fj", "sw", "sj", "mw", "mj",
  	  "nw", "nj", "rw", "rj" ]
  	  
  	private static final ESP_LR_CLUSTERS = [ "pɾ", "tɾ", "kɾ", "pl", "kl", "bɾ",
  	"dɾ", "ɡɾ", "bl", "ɡl", "fɾ", "fl" ]	
  	
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
	
	public List getWJClusters() {
  		return ESP_WJ_CLUSTERS
  	}
  	
  	public List getLRClusters() {
  		return ESP_LR_CLUSTERS
  	}
	
	public List PATT() {
		this.treatmentTargets = ["under", "construction"]
		return this.treatmentTargets	
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
		
		def basePhones = [
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
		def placeHeaders = ["", "bilabial", "", "labiodental", "", "interdental", "", 
		"alveolar", "", "palatoalveolar", "", "retroflex", "", "palatal", "", 
		"velar", "", "uvular", "", "pharyngeal", "", "glottal", ""]
		def mannerHeaders = ["plosive", "nasal", "fricative", "other fricative",
		"affricate", "approximant", "lateral", "glide"]
		
		def basePhoneMap = [:]
		
		phonemicInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : basePhones) {
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
		this.csv.writeNext(placeHeaders as String[])
		def csvRow = []
		
		basePhones.eachWithIndex { row, i ->
			csvRow.add(mannerHeaders[i])
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
		def basePhones = [
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
		def placeHeaders = ["", "bilabial", "", "labiodental", "", "interdental", "", 
		"alveolar", "", "palatoalveolar", "", "retroflex", "", "palatal", "", 
		"velar", "", "uvular", "", "pharyngeal", "", "glottal", ""]
		def mannerHeaders = ["plosive", "nasal", "fricative", "other fricative",
		"affricate", "approximant", "lateral", "glide"]
	
		def basePhoneMap = [:]
		
		phoneticInv.inventory.each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : basePhones) {
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
		this.csv.writeNext(placeHeaders as String[])
		def csvRow = []
		
		basePhones.eachWithIndex { row, i ->
			csvRow.add(mannerHeaders[i])
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

/* Prepare CSV file */
fileChooser = new JFileChooser()
fileChooser.setDialogTitle("Please specify a CSV file to output data")
fileChooser.setFileFilter(new FileNameExtensionFilter("CSV file", "csv"))
userSelection = fileChooser.showSaveDialog()
if (userSelection == JFileChooser.APPROVE_OPTION){
	csv = new CSVWriter(new FileWriter(fileChooser.getSelectedFile().getAbsolutePath()))
} else {
	JOptionPane.showMessageDialog(null, "You must choose a valid file to output data",
    "PATT", JOptionPane.WARNING_MESSAGE)
    return
}

def project = window.project
if (project == null) return

def sessionSelector = new SessionSelector(project)
def scroller = new JScrollPane(sessionSelector)
JOptionPane.showMessageDialog(window, scroller)
	
def sessions = sessionSelector.selectedSessions;
if(sessions.size() == 0) return

records = []
sessions.each { sessionLoc ->
	session = project.openSession(sessionLoc.corpus, sessionLoc.session)
	count = session.getRecordCount()
	println "Session: $sessionLoc \n\t $count records"
	records += session.records
}

speaker = new EnglishSpeaker(records, getBinding().out, csv)
println " ";
println "Phonological Assessment and Treatment Target Selection (PATT)"
println "*************************************************************"
speaker.writeCSV(speaker.phoneticInv)
speaker.writeCSV(speaker.phonemicInv)
speaker.writeCSV(speaker.clusterInv)
speaker.PATT()
println "*************************************************************"
println "TREATMENT TARGETS: " + speaker.treatmentTargets
println "Phones to monitor: " + speaker.outPhones
csv.writeNext("Phones to monitor:")
csv.writeNext(speaker.outPhones as String[] )
println "Phonemes to monitor: " + speaker.outPhonemes
csv.writeNext("Phonemes to monitor:")
csv.writeNext(speaker.outPhonemes as String[])
println "Clusters to monitor: " + speaker.outClusters
csv.writeNext("Clusters to monitor:")
csv.writeNext(speaker.outClusters as String[])

println "Please see output in " + fileChooser.getSelectedFile().getAbsolutePath()
csv.close()
