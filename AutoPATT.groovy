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

interface PhonInterface {
	void writeCSV(csv); /* Writes output to CSV */
	void gather(transcript, meaning, out); /* Collects data during pass thru Phon records */
}

/* Singleton class */
class SAEInventory {
	private static SAEInventory sae = null;
	/* Map of  Key:value pairs are phone:sonority 
	  QUESTION: what to do with things that shouldn't have a sonority distance,
	  such as affricate + glide combo? Making them null for now
	  QUESTION: What about the flap?
	  TODO: there is a difference between IPA g and regular g. GAH! FIX IT. 
	*/
  	private static final SAE_PHONES = [ "p":1, "b":2, "t":1, "d":2, 
  	"k":1, "ɡ":2, "f":5, "v":6, "θ":5, "ð":6, "s":5, "z":6, 
  	"ʃ":5, "ʒ":6, "ʧ":3, "ʤ":4, "m":7, "n":7, "ŋ":7, "l":8,
  	"ɹ":8, "w":9, "j":9, "h":9 ]
  	
  	private static final SAE_CLUSTERS = [ "tw", "kw", "pj", "kj", "bj", "pɹ", 
  	  "tɹ", "kɹ", "pl", "kl", "bɹ", "dɹ", "ɡɹ", "bl", "ɡl", "fj", "sw", "fɹ", 
  	  "θɹ", "ʃɹ", "fl", "sl", "vj", "mj", "sm", "sn", "sp", "st", "sk", "skw",
  	  "spɹ", "stɹ", "skɹ", "spl" ]
  	
  	protected SAEInventory() {
  	}
  	
  	public static SAEInventory get() {
  		if (sae == null) {
  			sae = new SAEInventory();
  		}
  		return sae;
  	}
  	
  	public List getPhones() {
  		return SAE_PHONES.keySet().collect()
  	}
  	
  	public List getClusters() {
  		return SAE_CLUSTERS
  	}
  	
  	/* Takes a 2-char cluster and determines sonority distance */
  	public Integer getSonorityDistance(String s) {
  		//out.println "Inside getSonorityDistance"
  		if (SAE_PHONES.containsKey(s[0]) && SAE_PHONES.containsKey(s[1])) {
  		  //out.println "Looking at $s"
  		  return SAE_PHONES[s[1]] - SAE_PHONES[s[0]]
  		}
  	}
    
  	/* Takes a list of 2-char clusters and returns smallest sonority distance */
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
}

class PhoneticInventory implements PhonInterface {
	/* 10-1-16: Would be great to implement PhoneticInventoryVisitor to visit 
	phones and process them the way a phonetic inventory would. Think about 
	this for the future. It could filter out diphthongs for example.  */
	static private IPATokens ipaTokens = new IPATokens();
	static private SAEInventory sae = SAEInventory.get();
	private Map inventoryMap;
	private List englishPhones, outPhones;
	
	PhoneticInventory() {
		inventoryMap = [:]
		/* TODO: account for compoundphones when working with affricates */
		englishPhones = sae.getPhones();
	}
	
	
	/* This is where I'll implement printing phonetic inventory by place,
	manner, and articulation */
		void writeCSV(csv) {
		/* Algorithm something like this: 
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
		
		getInventory().each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : basePhones) {
		  	  for (String item : row) {
		  	  	  if ( !(ipa[0] instanceof CompoundPhone) && 
		  	  	  (Character) item == ipa[0].basePhone) {
		  	  	  	  //out.println "Found a match"
		  	  	  	  if (basePhoneMap[item])
		  	  	  	  	  basePhoneMap[item].add(ipa[0])
		  	  	  	  else 
		  	  	  	  	  basePhoneMap[item] = [ ipa[0] ]
		  	  	  	  		  	  	  	  
		  	  	  	  break mainLoop
		  	  	  }
		  	  }
		  }
		}
		
		csv.writeNext("PHONETIC INVENTORY:")
		csv.writeNext(placeHeaders as String[])
		def csvRow = []
		
		basePhones.eachWithIndex { row, i ->
			csvRow.add(mannerHeaders[i])
			row.each  {
				if (basePhoneMap[it]) {
					//out.printf basePhoneMap[it].join(",") + " \r"
					csvRow.add(basePhoneMap[it].join(","))
				} else {
					//out.printf " "
					csvRow.add(" ")
				}
			}
			csv.writeNext(csvRow as String[])
			csvRow = []
		}
		csv.writeNext("")
	}
	
	void sortedDisplay(out) {
		/* find all phones that occurred twice */
		//def phones = inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
		out.println "Phonetic Inventory: " + getInventory().sort()
		//outPhones = getOutPhones();
		//out.println "Target OUT Phones: " + outPhones.sort()
	}
	
	void gather(transcript, meaning, out) {
		transcript.findAll { it instanceof Phone}.each {
			/* For a compound tesh, getBasePhone() outputted an 'x' */
			//out.println "$it - " + ipaTokens.getTokenType( it.getBasePhone() );
			if ( isConsonant(it) ) {
				def phone = it.getText();
				//def phone = it; //attempting to store the object, not string
				/* Count occurrences of each phone */
				def i = inventoryMap[phone];
				inventoryMap[phone] = i ? ++i : 1;
			}
		}
	}
	
	/* inventory returns consonants occurring twice in list form */
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
	
	List getOutPhones() {
		def phones = getInventory()
		return englishPhones - englishPhones.intersect( phones );
	}
}

class PhonemicInventory implements PhonInterface {
	/*TODO: consider implementing PhonemicInventory as a type of PhoneticInventory
	since you are using almost the exact same members and methods for each */
	static private SAEInventory sae = SAEInventory.get();
	private Map inventoryMap, meanings;
	private List phoneticInv;
	private List englishPhones, outPhonemes;
	private IpaTernaryTree minPairs;
	
	PhonemicInventory() {
		inventoryMap = [:]
		meanings = [:]
		/* TODO: account for compoundphones when working with affricates 
		Also, this variable belongs in its own class...*/
		englishPhones = sae.getPhones();
		minPairs = new IpaTernaryTree( 
		  new CompoundFeatureComparator(
		     FeatureComparator.createPlaceComparator()));
	}
	
	
	/* This is where I'll increment printing phonetic inventory by place,
	manner, and articulation */
	void writeCSV(csv) {
		/* Algorithm something like this: 
		1) Have pre-created 8x22 IPA list of consonants, all values set to false
		and pre-created map which will store key:values of all variants of the 
		basePhone, e.g. basePhoneMap["t":["tʷ","t","t̪"]]
		2) Go through each phone in phonetic inventory, convert to IPATranscript
		3) get Phone from IPATranscript.
		4) get basePhone from Phone.
		5) if basePhone VALUE is null, create list, otherwise append inventory
		phone to existing list.
		6) for each row in map, filter out and print all true values.*/
		
		def wordKeys = minPairs.keySet();
		csv.writeNext("Minimal Pairs:")
		wordKeys.each { key ->
			def pairs = minPairs.get(key) as Queue;
			
			if(pairs.size() > 0) {
				pairs.offerFirst(key)
				csv.writeNext(pairs as String[])
				//csv.writeNext("$key:$pairs")
			}
		}
		csv.writeNext("")
		
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
		
		getInventory().each { phone ->
		  IPATranscript ipa = IPATranscript.parseIPATranscript(phone)
		  mainLoop:
		  for (List row : basePhones) {
		  	  for (String item : row) {
		  	  	  if ((Character) item == ipa[0].basePhone) {
		  	  	  	  //out.println "Found a match"
		  	  	  	  if (basePhoneMap[item])
		  	  	  	  	  basePhoneMap[item].add(ipa[0])
		  	  	  	  else 
		  	  	  	  	  basePhoneMap[item] = [ ipa[0] ]
		  	  	  	  		  	  	  	  
		  	  	  	  break mainLoop
		  	  	  }
		  	  }
		  }
		}
		
		csv.writeNext("PHONEMIC INVENTORY:")
		csv.writeNext(placeHeaders as String[])
		def csvRow = []
		
		basePhones.eachWithIndex { row, i ->
			csvRow.add(mannerHeaders[i])
			row.each  {
				if (basePhoneMap[it]) {
					//out.printf basePhoneMap[it].join(",") + " \r"
					csvRow.add(basePhoneMap[it].join(","))
				} else {
					//out.printf " "
					csvRow.add(" ")
				}
			}
			csv.writeNext(csvRow as String[])
			csvRow = []
		}
		csv.writeNext("")
	}
	
	void gather(transcript, orthography, out) {
		def words = orthography.toString().tokenize()
		if (words.count{it} != transcript.words().count{it})
		  out.println "Orthography <-> IPA Actual Count Mismatch!"
		  
		transcript.words().findAll{it.contains("\\w")}.eachWithIndex { utt, i ->
			//every word in the word tree gets an empty hash set.
			minPairs.put(utt, new HashSet<IPATranscript>());
			//out.println "meanings[$utt] = ${words[i]}"
			/*map the utterance to orthography. This is too rudimentary. What
			if the child has the same utterance for different meanings? What
			about homonyms? It needs to be a mapping to a list, not a string. 
			It's not a 1:1 mapping*/
			meanings[utt.toString()] = words[i];
		}
	}
	
	void buildInventory(phoneticInv, out) {
		buildMinPairs();
		
		def wordKeys = minPairs.keySet();
		def doneContrast = []; /* For rule 4 */
		this.phoneticInv = phoneticInv.inventory;
		
		wordKeys.each { key ->
			def pairs = minPairs.get(key);
			
			pairs.each { pair ->
				/* Go through each phone in both keys until we find contrast */
				for (i in 0 .. key.length() - 1) {
					/* phones to compare */
					def p1 = key[i];
					def p2 = pair[i];
					
					if ( PhoneticInventory.isConsonant(p1) &&
					PhoneticInventory.isConsonant(p2) && 
				    this.phoneticInv.contains(p1.getText()) &&
				    meanings[key.toString()] != meanings[pair.toString()] ) {
						/* if the consonants don't match we should have a 
						minimal pair with a consonant contrast here */
						if ( p1.getText() != p2.getText() && !(doneContrast[i])) {
						  
						  //out.println "Comparing type " + key[i].getClass() + 
						  //  " and type " + pair[i].getClass();
					      
					      /* QUESTION: Does it count if we compare
					      against something NOT in the phonetic inventory? 
                                                 ANSWER: YES 
                             QUESTION: For contrasts in meaning, should
                             we care about homonyms? */					      
					      
						  def count = inventoryMap[ p1.getText() ];
						  inventoryMap[ p1.getText() ] = count ? ++count : 1;
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
		def wordKeys = minPairs.keySet();
		
		wordKeys.each { key ->
			def pairs = minPairs.get(key);
			
			wordKeys.each { key2 ->
				if(key == key2) return;
				if(key.length() != key2.length()) return; /* My addition -rsa */
				if(meanings[key.toString()] == meanings[key2.toString()]) return; /* Also mine -rsa */
				if(key.getExtension(LevenshteinDistance).distance(key2) == 1) {
					for (i in 0 .. key.length() - 1) {
						def p1 = key[i];
						def p2 = key2[i];
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
	
	/* inventory returns consonants occurring twice in list form */
	List getInventory() {
		return inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}
	
	List getOutPhonemes() {
		def phonemes = getInventory()
		return englishPhones - englishPhones.intersect( phonemes );
	}	
}

class ClusterInventory implements PhonInterface {
	static private PhonexPattern pattern = 
	  PhonexPattern.compile("^\\s<,1>(cluster=\\c\\c+)")
	static private SAEInventory sae = SAEInventory.get();
	Map inventoryMap = [:]
	private List englishClusters, outClusters;
	
	ClusterInventory() {
		englishClusters = sae.getClusters()
	}
	

	void gather(transcript, meaning, out) {
		/* find two- and three- element word-initial clusters */
		transcript.words().each { word->
			def PhonexMatcher matcher = pattern.matcher(word)
			if (matcher.find()) {
				def clusterTokens = matcher.group(pattern.groupIndex("cluster"))
				def cluster = clusterTokens.join()
				def count = inventoryMap[ cluster ]
				inventoryMap[ cluster ] = count ? ++count : 1;
			}
		}
	}
	
void writeCSV(csv) {
		def clusters = getInventory()
		csv.writeNext("CLUSTER INVENTORY:")
		csv.writeNext(clusters.sort{x,y ->
			sae.getSonorityDistance(x) <=> sae.getSonorityDistance(y)
			} as String[])
		//outClusters = getOutClusters();
		//out.println "Target OUT Clusters: " + outClusters.sort()
		csv.writeNext("")
	}
	
	List getInventory() {
		return inventoryMap.findAll{ it.value > 1 }.collect{ it.key }
	}
	
	List getOutClusters() {
		def clusters = getInventory()
		return englishClusters - englishClusters.intersect( clusters );
	}
}

void doSessions(List pattObjects, out) {
	def project = window.project
	if (project == null) return;
	
	// Display session selector and obtain a list of selected session locations.
	/* For learning purposes, let's just grab all sessions from the project 
	println "Project: " + window.project.name;
	corpora = window.project.getCorpora();
	corpora.each { corpus -> 
		println "Corpus: $corpus";*/
		
		def sessionSelector = new SessionSelector(project);
		def scroller = new JScrollPane(sessionSelector);
		JOptionPane.showMessageDialog(window, scroller);
		
		def sessions = sessionSelector.selectedSessions;
		if(sessions.size() == 0) return;
		
		//Display combo box to choose speaker
		//def speakerBox = new JComboBox()
		//sessions = window.project.getCorpusSessions(corpus);
			sessions.each { sessionLoc ->
				session = project.openSession(sessionLoc.corpus, sessionLoc.session)
				//def speakerBox = new JComboBox()
				//session.getParticipants().each { speakerBox.addItem(it.name) }
				//JOptionPane.showMessageDialog(window, speakerBox)
				count = session.getRecordCount();
				println "Session: $sessionLoc \n\t $count records";
				session.records.each { record ->
					orthography = record.getOrthography()
					ipaActual = record.getIPAActual()
				
				/* create list of tuples called "meanings" to put productions 
				(IPAActual) together with their meanings (orthography) */
					meanings = []
					orthography.eachWithIndex { tier, index ->
					    //out.println "Adding ${ipaActual[index]}, $tier to meanings..."
						meanings.add([ipaActual[index], tier]) 
					}
				
				/* Some trouble understanding record.IPAActual, but I believe
				it is just raw Unicode. The type is ca.phon.session.impl.Tierimpl
				and when iterated over in an 'each' or for loop, it becomes
				ca.phon.ipa.IPATranscript */
				// record.IPAActual.each { transcript ->
					/* Each element of an IPATranscript is a 
					Phone, CompoundPhone, StressMarker, WordBoundary, or 
					other types defined in ca.phon.ipa */
					//pattObjects.each { it.gather(transcript) }
				//}
					meanings.each { production, meaning  ->
						pattObjects.each { it.gather(production, meaning, out) }
					}
				}
			}
}

/* This function represents Part 2:Step 1 of the PATT process, which determines
if a 3-element cluster is an appropriate treatment target. It returns a List
if successful, and null if there are no targets. */
List PATTStepOne(phonemicInv, clusterInv, out) {
	out.println "In PATTStepOne()"
	/* if any s/CC/ clusters are in the inventory, return null 
	QUESTION: is this only any ENGLISH clusters, or ANY s/CC/? Assuming any
	QUESTION: Any /s/? Or should it be any /s/ with no diacritics?
	*/
	clusters = clusterInv.inventory
	targets = []
	for (cluster in clusters ) {
		IPATranscript ipa = IPATranscript.parseIPATranscript(cluster)
		if (ipa[0].basePhone == (Character) "s" && cluster.length() >= 3) {
			out.println "Found /s/ cluster"
			return [];
		}
	}
	
	/* if "kw", "pr", "tr", "kr", or "pl" can be constructed from phonemes
	in the phonemic inventory, prepend /s/ to it and return it as a target.
	QUESTION: count base phones or not? 
	NOTE: If we want to extend this process to non-english languages,
	allowedClusters cannot be hard-coded */
	allowedClusters = ["kw", "pɹ", "tɹ", "kɹ", "pl"]
	c2s = phonemicInv.inventory.findAll{ ["p", "t", "k"].contains(it) }
	c3s = phonemicInv.inventory.findAll{ ["w", "l", "ɹ"].contains(it) }
	for (c2 in c2s ) {
		for (c3 in c3s) {
			if ( allowedClusters.contains(c2 + c3) ) {
				out.println "Looking at $c2 and $c3"
				targets.add("s"+c2+c3)
			}
		}
	}
	return targets
}

List PATTStepTwo(clusterInv, phoneticInv, csv) {
	SAEInventory sae = SAEInventory.get()
	targetPool = sae.clusters.findAll{it.length() == 2}
	//println "PATTStepTwo: targetPool: " + targetPool
	
	/* Remove IN clusters */
	println "PATTStepTwo: Removing IN clusters..."
	targetPool = targetPool - targetPool.intersect( clusterInv.inventory )
	println "PATTStepTwo: targetPool: " + targetPool
	
	if (!targetPool) return null
	
	/* Get minimum sonority distance (MSD) and remove all clusters with MSD
    greater than or equal to that sonority distance */
    println "Getting cluster inventory of 2"
    clusters = clusterInv.inventory.findAll{ it.length() == 2 }
    /* If there aren't any clusters in the inventory, set sonority distance to
    a number beyond the max. This is a hack that needs to be fixed */
    println "Attempting function getMinSonorityDistance"
    msd = sae.getMinSonorityDistance(clusters)
    if (msd == null) msd = 10
    println "PATTStepTwo: msd: $msd"
    removables = targetPool.findAll{ sae.getSonorityDistance(it) >= msd }
    println "PATTStepTwo: removables: $removables"
    targetPool = targetPool - targetPool.intersect( removables )
    println "PATTStepTwo: Removed clusters with msd >= $msd"
    println "PATTStepTwo: targetPool: " + targetPool
    
    if (!targetPool) return null
    
    /* Remove clusters with SD=-2 and /C/j clusters 
       Note: if you're working with non-english, the /C/j clusters should
       not be hard coded
       Note: due to changes in sonority, SD=-2 is now SD=-4*/
    removables = targetPool.findAll{ sae.getSonorityDistance(it) == -4 || 
    ["pj", "bj", "fj", "vj", "mj"].contains(it) }
    println "PATTStepTwo: removables: $removables"
    targetPool = targetPool - targetPool.intersect( removables )
    println "PATTStepTwo: Removed /C/j clusters and SD=-2 clusters"
    println "PATTStepTwo: targetPool: " + targetPool
    
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
    	println "PATTStepTwo: Consider " + removables.join(",")
    	csv.writeNext("Potential Cluster Targets after Step Two: ")
    	csv.writeNext(removables as String[])
    }
    println "PATTStepTwo: Removed sw, sl, sm, sn for now"
    println "PATTStepTwo: targetPool: " + targetPool
    
    if (!targetPool) return null
    	
    /* Find smallest sonority distance in targetPool. If there is more
than one, find those with an OUT phone and return them as a treatment
target list. */
    msd = sae.getMinSonorityDistance(targetPool)
    removables = targetPool.findAll{ sae.getSonorityDistance(it) > msd }
    targetPool = targetPool - targetPool.intersect( removables )
    println "PATTStepTwo: Removed all from target pool >= $msd"
    println "PATTStepTwo: targetPool: " + targetPool
    outPhones = phoneticInv.outPhones
    targets = []
    for (cluster in targetPool) {
    	for (phone in cluster)
    	  if (outPhones.contains(phone)) {
    	  	  targets.add(cluster)
    	  	  break
    	  }
    }
    return targets
}

List PATTStepThree(phoneticInv) {
	targetPool = phoneticInv.outPhones
	
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

phoneticInv = new PhoneticInventory()
phonemicInv = new PhonemicInventory()
clusterInv = new ClusterInventory()

pattObjects = [phoneticInv, phonemicInv, clusterInv];
//pattObjects = [clusterInv]

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



doSessions(pattObjects, getBinding().out)
phonemicInv.buildInventory(phoneticInv, getBinding().out);

println " ";
println "Phonological Assessment and Treatment Target Selection (PATT)"
println "*************************************************************"
//println "Phone Map: " + phoneMap;
//pattObjects.each { it.display(getBinding().out) }
pattObjects.each { it.writeCSV(csv) }

/* new row */
csv.writeNext("")

targets = PATTStepOne(phonemicInv, clusterInv, getBinding().out)

/* Can be made into a for loop with check on null-ness */
if (targets) {
	println "Targets after Step One: " + targets
	csv.writeNext("Targets after Step One: ")
	csv.writeNext(targets as String[])
} else {
	println "No targets after Step One"
	csv.writeNext("No targets after Step One")
	targets = PATTStepTwo(clusterInv, phoneticInv, csv)
	if (targets) {
		println "Targets after Step Two: " + targets
		csv.writeNext("Targets after Step Two: ")
		csv.writeNext(targets as String[])
	} else {
		println "No targets after Step Two"
		csv.writeNext("No targets after Step Two")
		targets = PATTStepThree(phoneticInv)
		if (targets) {
			println "Targets after Step Three: " + targets
			csv.writeNext("Targets after Step Three: ")
			csv.writeNext(targets as String[])
		} else {
			println "Error: no targets found!"
			csv.writeNext("Error: no targets found!")
		}
	} 
}
println "*************************************************************"
println "Phones to monitor: " + phoneticInv.outPhones
csv.writeNext("Phones to monitor:")
csv.writeNext(phoneticInv.outPhones as String[] )
println "Phonemes to monitor: " + phonemicInv.outPhonemes
csv.writeNext("Phonemes to monitor:")
csv.writeNext(phonemicInv.outPhonemes as String[])
println "Clusters to monitor: " + clusterInv.outClusters
csv.writeNext("Clusters to monitor:")
csv.writeNext(clusterInv.outClusters as String[])

println "Please see output in " + fileChooser.getSelectedFile().getAbsolutePath()
csv.close()
return
