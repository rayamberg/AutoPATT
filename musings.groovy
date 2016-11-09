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
	PhoneticInventory(records, out) {
		inventoryMap = [:]
		records.each{ record ->
			record.IPAActual.each { transcript ->
				transcript.findAll { it instanceof Phone}.each {
					/* For a compound tesh, getBasePhone() outputted an 'x' */
					out.println "$it - " + ipaTokens.getTokenType( it.getBasePhone() )
					if ( isConsonant(it) ) {
						def phone = it.text
						//def phone = it; //attempting to store the object, not string
						/* Count occurrences of each phone */
						def i = inventoryMap[phone]
						inventoryMap[phone] = i ? ++i : 1
					}
				}
			}
		}
	}
	
	List getInventory() {
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
		this.phoneticInv = new PhoneticInventory(records, out)
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
						  
						  //out.println "Comparing type " + key[i].getClass() + 
						  //  " and type " + pair[i].getClass();
					      
					      /* QUESTION: Does it count if we compare
					      against something NOT in the phonetic inventory? 
                                                 ANSWER: YES 
                             QUESTION: For contrasts in meaning, should
                             we care about homonyms? */					      
					      
						  def count = this.inventoryMap[ p1.text ];
						  this.inventoryMap[ p1.text ] = count ? ++count : 1;
						  doneContrast[i] = 1;
						  /*out.println "Non-match between $p1 and $p2, inventoryMap[$p1] = " +
						    inventoryMap[p1.getText()];*/
						  out.println "$key/$pair: Found contrast for $p1 and $p2"
						  /*out.println "meanings[$key] = " + meanings[key.toString()] + 
						    "; meanings[$pair] = " + meanings[pair.toString()]*/
					    }
					}	
				}
			}
			doneContrast = []; /*clear doneContrast for next word*/
		}	
		
	}

	IpaTernaryTree buildMinPairs() {
		def wordKeys = minPairs.keySet()
		
		wordKeys.each { key ->
			def pairs = minPairs.get(key)
			
			wordKeys.each { key2 ->
				if(key == key2) return
				if(key.length() != key2.length()) return /* My addition -rsa */
				if(meanings[key.toString()] == meanings[key2.toString()]) return /* Also mine -rsa */
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

abstract class Speaker implements Language {
/* Should contain all important data structures for analyzing
a client's phonetic, phonemic, and cluster inventories; the steps for 
determining treatment target selection; the inventory "rules" for the specific
language, e.g. allowable phones in English, allowable clusters in Spanish, etc.,
and an interface to output this information. */
	private CSVWriter csv
    public PhoneticInventory phoneticInv
  	public PhonemicInventory phonemicInv
  	public ClusterInventory clusterInv
  	public List treatmentTargets
  	
  	public abstract List getModelPhones() 
  	public abstract List getModelClusters()
  	public abstract Map getSonorityValues()
  	public abstract List PATT()
  	
  	public Speaker(records, out, csv) {
  		this.phonemicInv = new PhonemicInventory(records, out)
  		this.phoneticInv = this.phonemicInv.phoneticInv
  		this.clusterInv = new ClusterInventory(records, out)
  		this.csv = csv
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
  		//out.println "Inside getMinSonorityDistance"
  		def msd = null
  		for (cluster in clusters) {
  			def distance = getSonorityDistance(cluster)
  			if (distance == null) continue
  			if ( msd == null || distance < msd )
				msd = distance
		}
			//out.println "msd is $msd"
		return msd
  	}
  	
  	public void writeCSV() {
  		this.csv.writeNext("Hello World")
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
		this.treatmentTargets = ["under", "construction"]
		return this.treatmentTargets
	}
	
	private List PATTStepOne() {
		//out.println "In PATTStepOne()"
		/* if any s/CC/ clusters are in the inventory, return empty 
		QUESTION: is this only any ENGLISH clusters, or ANY s/CC/? Assuming any
		QUESTION: Any /s/? Or should it be any /s/ with no diacritics?
		*/
		targets = []
		for (cluster in this.clusters ) {
			IPATranscript ipa = IPATranscript.parseIPATranscript(cluster)
			if (ipa[0].basePhone == (Character) "s" && cluster.length() >= 3) {
				//out.println "Found /s/ cluster"
				return [];
			}
		}
		
		/* if "kw", "pr", "tr", "kr", or "pl" can be constructed from phonemes
		in the phonemic inventory, prepend /s/ to it and return it as a target.
		QUESTION: count base phones or not? */
		allowedClusters = ["kw", "pɹ", "tɹ", "kɹ", "pl"]
		c2s = this.phonemes.findAll{ ["p", "t", "k"].contains(it) }
		c3s = this.phonemes.findAll{ ["w", "l", "ɹ"].contains(it) }
		for (c2 in c2s ) {
			for (c3 in c3s) {
				if ( allowedClusters.contains(c2 + c3) ) {
					//out.println "Looking at $c2 and $c3"
					targets.add("s"+c2+c3)
				}
			}
		}
		return targets	
	}

	private List PATTStepTwo() {
		targetPool = this.modelClusters.findAll{it.length() == 2}
		//println "PATTStepTwo: targetPool: " + targetPool
		
		/* Remove IN clusters */
		//println "PATTStepTwo: Removing IN clusters..."
		targetPool = targetPool - targetPool.intersect( this.clusters )
		//println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
		
		/* Get minimum sonority distance (MSD) and remove all clusters with MSD
		greater than or equal to that sonority distance */
		//println "Getting cluster inventory of 2"
		clusters = this.clusters.findAll{ it.length() == 2 }
		/* If there aren't any clusters in the inventory, set sonority distance to
		a number beyond the max. This is a hack that needs to be fixed */
		//println "Attempting function getMinSonorityDistance"
		msd = this.getMinSonorityDistance(clusters)
		if (msd == null) msd = 10
		//println "PATTStepTwo: msd: $msd"
		removables = targetPool.findAll{ this.getSonorityDistance(it) >= msd }
		println "PATTStepTwo: removables: $removables"
		targetPool = targetPool - targetPool.intersect( removables )
		println "PATTStepTwo: Removed clusters with msd >= $msd"
		println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
		
		/* Remove clusters with SD=-2 and /C/j clusters 
		   Note: if you're working with non-english, the /C/j clusters should
		   not be hard coded
		   Note: due to changes in sonority, SD=-2 is now SD=-4*/
		removables = targetPool.findAll{ this.getSonorityDistance(it) == -4 || 
		["pj", "bj", "fj", "vj", "mj"].contains(it) }
		//println "PATTStepTwo: removables: $removables"
		targetPool = targetPool - targetPool.intersect( removables )
		//println "PATTStepTwo: Removed /C/j clusters and SD=-2 clusters"
		//println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
			
		/* If the pool contains /sw, sl, sm, or sn/ determine the error pattern in 
	these clusters, e.g. /sn/->/s/ or /sn/->n. If error patterns are similar to
	/sp, st, sk/, remove the clusters. If they are different, keep them in pool. If
	it is unclear, remove the clusters
	NOTE: A MORE CLEAR ALGORITHM IS NECESSARY HERE. TALK TO DR. BARLOW 
	NOTE: This will involve comparing orthography or IPA Target to IPA Actual
	QUESTION: What if they're similar to other s clusters, but not similar to non-s
	clusters? How should we deal with non-s clusters 
	NOTE: For now, I'm just going to remove the clusters. 
	PHIL: Maybe separate these clusters for now, and print them out separately
	as "here are your potential 2-element 's' clusters */
		//clusters = clusterInv.inventory.findAll{ ["sw", "sl", "sm", "sn"].contains(it) }
		removables = targetPool.findAll{ ["sw", "sl", "sm", "sn"].contains(it) }
		targetPool = targetPool - targetPool.intersect( removables )
		if (removables) {
			/* This is where the potential 2-element clusters can be added
			to the CSV file */
			//println "PATTStepTwo: Consider " + removables.join(",")
			//csv.writeNext("Potential Cluster Targets after Step Two: ")
			//csv.writeNext(removables as String[])
		}
		//println "PATTStepTwo: Removed sw, sl, sm, sn for now"
		//println "PATTStepTwo: targetPool: " + targetPool
		
		if (!targetPool) return null
			
		/* Find smallest sonority distance in targetPool. If there is more
	than one, find those with an OUT phone and return them as a treatment
	target list. */
		msd = this.getMinSonorityDistance(targetPool)
		removables = targetPool.findAll{ this.getSonorityDistance(it) > msd }
		targetPool = targetPool - targetPool.intersect( removables )
		//println "PATTStepTwo: Removed all from target pool >= $msd"
		//println "PATTStepTwo: targetPool: " + targetPool
		targets = []
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
		targetPool = this.outPhones
		
		/* First, remove all stimulable sounds: since we haven't implemented 
		stimulable sounds yet, skipping this...*/
		
		/* Cross out early acquired sounds. For english this is [p, b, t, d, k, g,
		f, v, m, n, ŋ, w, j, h] */
		earlySounds = ["p", "b", "t", "d", "k", "ɡ", "f", "v", "m", "n", "ŋ", "w",
					   "j", "h"]
		targetPool = targetPool - targetPool.intersect( earlySounds )
		
		/* Choose sounds that will lead to the most system-wide change. 
		NOTE: THIS ALGORITHM NEEDS TO BE MADE MORE CLEAR. CHECK WITH DR. BARLOW. For
		example, if we see an affricate, do we ignore everything else? Do we pick
		all sounds that trump another sound? Skipping this step for now */
		
		/* From the list of common sounds sorted from most to least, pick the first
		one we see from the targetPool 
		NOTE: need to change from hard coding to account for other languages */
		commonSounds = ["ɹ", "l", "s", "z", "ð", "θ", "ʃ", "ʤ", "ʧ", "ʒ"]
		targets = []
		for (sound in commonSounds)
			if (targetPool.contains(sound)) targets.add(sound)
		
		return targets
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

eng = new EnglishSpeaker(records, getBinding().out, csv)
eng.writeCSV()
csv.close()
