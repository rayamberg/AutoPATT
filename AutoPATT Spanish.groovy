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
class ESPInventory {
	private static ESPInventory esp = null;
	/* Map of  Key:value pairs are phone:sonority 
	  QUESTION: what to do with things that shouldn't have a sonority distance,
	  such as affricate + glide combo? Making them null for now
	  QUESTION: What about the flap?
	  TODO: there is a difference between IPA g and regular g. GAH! FIX IT. 
	*/
  	private static final ESP_PHONES = [ "p":1, "b":2, "t":1, "d":2, 
  	"k":1, "ɡ":2, "f":3, "s":3, "x":3, "ʧ":4, "m":5, "n":5, "ɳ":5, "r":6,
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
  	
  	protected ESPInventory() {
  	}
  	
  	public static ESPInventory get() {
  		if (esp == null) {
  			esp = new ESPInventory();
  		}
  		return esp;
  	}
  	
  	public List getPhones() {
  		return ESP_PHONES.keySet().collect()
  	}
  	
  	public List getClusters() {
  		return ESP_CLUSTERS
  	}

  	public List getWJClusters() {
  		return ESP_WJ_CLUSTERS
  	}
  	
  	public List getLRClusters() {
  		return ESP_LR_CLUSTERS
  	}
  	
  	public Integer getSonorityDistance(String s) {
  		//out.println "Inside getSonorityDistance"
  		if (ESP_PHONES.containsKey(s[0]) && ESP_PHONES.containsKey(s[1])) {
  		  //out.println "Looking at $s"
  		  return ESP_PHONES[s[1]] - ESP_PHONES[s[0]]
  		}
  	}
  
  	public Integer getMinSonorityDistance(List clusters, out) {
  		//out.println "Inside getMinSonorityDistance"
  		def msd = null
		for (cluster in clusters) {
			def i = getSonorityDistance(cluster)
			if (i == null) continue
			if ( msd == null || i < msd )
				msd = i
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
	static private ESPInventory esp = ESPInventory.get();
	private Map inventoryMap;
	private List spanishPhones, outPhones;
	
	PhoneticInventory() {
		inventoryMap = [:]
		/* TODO: account for compoundphones when working with affricates */
		spanishPhones = esp.getPhones();
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
		return spanishPhones - spanishPhones.intersect( phones );
	}
}

class PhonemicInventory implements PhonInterface {
	/*TODO: consider implementing PhonemicInventory as a type of PhoneticInventory
	since you are using almost the exact same members and methods for each */
	static private ESPInventory esp = ESPInventory.get();
	private Map inventoryMap, meanings;
	private List phoneticInv;
	private List spanishPhones, outPhonemes;
	private IpaTernaryTree minPairs;
	
	PhonemicInventory() {
		inventoryMap = [:]
		meanings = [:]
		/* TODO: account for compoundphones when working with affricates 
		Also, this variable belongs in its own class...*/
		spanishPhones = esp.getPhones();
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
		return spanishPhones - spanishPhones.intersect( phonemes );
	}	
}

class ClusterInventory implements PhonInterface {
	static private PhonexPattern pattern = 
	  PhonexPattern.compile("^\\s<,1>(cluster=\\c\\c+)")
	static private ESPInventory esp = ESPInventory.get();
	Map inventoryMap = [:]
	private List spanishClusters, outClusters;
	
	ClusterInventory() {
		spanishClusters = esp.getClusters()
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
			esp.getSonorityDistance(x) <=> esp.getSonorityDistance(y)
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
		return spanishClusters - spanishClusters.intersect( clusters );
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

/* This function represents Part 2:Step 1 of the Spanish PATT process, which determines
if a 2-element cluster is an appropriate treatment target. It returns a List
if successful, and null if there are no targets.  */
List PATTStepOne(phonemicInv, clusterInv, out) {
	out.println "In PATTStepOne()"
	/* Algorithm: you'll have two separate cluster lists. One for C+/w,j/ 
	clusters, and one for C+/l,r/ clusters. This might involve modifying the
	cluster inventory for Spanish.*/
	ESPInventory esp = ESPInventory.get()
	clusters = clusterInv.inventory
	WJClusters = clusters.intersect( esp.getWJClusters() )
	LRClusters = clusters.intersect( esp.getLRClusters() )
	WJTargetPool = esp.getWJClusters()
	LRTargetPool = esp.getLRClusters()
	/* 1. cross out all IN clusters from both charts. If both empty go to 
	Step 2. */
	WJTargetPool = WJTargetPool - WJTargetPool.intersect(WJClusters)
	LRTargetPool = LRTargetPool - LRTargetPool.intersect(LRClusters)
	if (!(WJTargetPool || LRTargetPool)) return null
	
	/* 2. Get MSD for /w,j/ clusters. Cross out all clusters >= MSD.*/
    if (WJTargetPool) {
    	out.println "PATTStepOne: Looking at C+/w,j/ clusters"
    	msd = esp.getMinSonorityDistance(WJClusters, getBinding().out)
    	if (msd == null) msd = 10
    	removables = WJTargetPool.findAll{ esp.getSonorityDistance(it) >= msd }
        WJTargetPool = WJTargetPool - WJTargetPool.intersect( removables )
    }
   
    /*3. Get MSD for /l,r/ clusters. Cross out all clusers >= MSD. */
    if (LRTargetPool) {
    	out.println "PATTStepOne: Looking at C+/l,ɾ/ clusters"
    	msd = esp.getMinSonorityDistance(LRClusters, getBinding().out)
    	if (msd == null) msd = 10
    	removables = LRTargetPool.findAll{ esp.getSonorityDistance(it) >= msd }
        LRTargetPool = LRTargetPool - LRTargetPool.intersect( removables )
    }
    
    /* 4. If pool empty, go to Step 2. */
    if (!(WJTargetPool || LRTargetPool)) return null
    
    /*5. Choose clusters with smallest sonority distance. If more than one is 
	chosen, select one that includes OUT phones. If there are more than one,
	consider selecting one of each type from /w,j/ and /l,r/. Those will be 
	your treatment targets. Return targets */
	targets = []
	//Get smallest sonority distance for C+/w,j/ targets and C+/l,r/ targets
	//separately and add them to targets list.
	msd = esp.getMinSonorityDistance(WJTargetPool, getBinding().out)
    WJTargets = WJTargetPool.findAll{ esp.getSonorityDistance(it) == msd }
    msd = esp.getMinSonorityDistance(LRTargetPool, getBinding().out)
    LRTargets = LRTargetPool.findAll{ esp.getSonorityDistance(it) == msd }
	targets = WJTargets + LRTargets
	
	return targets
}

List PATTStepTwo(clusterInv, phoneticInv, csv) {
	/* Algorithm:
	1. Get all out phones.
	2. Cross out all stimulable sounds
	3. Cross out early acquired sounds [p t k m n ñ l j x]
	4. Circle those with greatest complexity.
	5. If multiple sounds, select one that occurs most frequently.
	In Spanish this is [s l n t edh r t m B velar f z j r x tS ng] 
	6. Return singleton target */
	targetPool = phoneticInv.outPhones
	
	/* First, remove all stimulable sounds: since we haven't implemented 
	stimulable sounds yet, skipping this...*/
	
	/* Cross out early acquired sounds. For Spanish this is [p, t, k, m, n, 
	ñ, l, j, x] */
	earlySounds = ["p", "t", "k", "m", "n", "ñ", "l", "j", "x"]
	targetPool = targetPool - targetPool.intersect( earlySounds )
	
	/* Choose sounds that will lead to the most system-wide change. 
	NOTE: THIS ALGORITHM NEEDS TO BE MADE MORE CLEAR. CHECK WITH DR. BARLOW. For
	example, if we see an affricate, do we ignore everything else? Do we pick
	all sounds that trump another sound? Skipping this step for now */
	
	/* From the list of common sounds sorted from most to least, pick the first
	one we see from the targetPool 
	NOTE: need to change from hard coding to account for other languages */
	commonSounds = ["s", "l", "n", "t", "ð", "ɾ", "m", "p", "β", "ɣ", "f", "z",
	                "j", "r", "x", "ʧ", "ɳ"]
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
		println "Error: no targets found!"
		csv.writeNext("Error: no targets found!")
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


/*  
   TODO: Sort phonetic inventory by place and manner
   3/24/16 - Got something basic and ugly working but it should not need to be coded more than once!!
   8/11/16 - sorting by sonority distance in cluster inventory now. In the CSV printout, might
   be good to loop through all sonority values and group together all clusters with those vals
   in a single cell.
  
   For mismatches, the user can be given a menu to stop the 
   query, include the record, exclude, etc. (P3)
   8/7/16 - Looks like we need to split on whitespace to map utterances to orthography.
   Phil will write to Phon folks, and I found OrthoWordExtractor, which actually didn't
   help. I may code a regex later to fix this, but right now I want to wait for the Phon
   people to let us know if there is a more elegant solution.
   
   TODO: resolve Step 2, Part D so that clusters are handled appropriately.
   Dr Barlow's recommendation: It can spit out two lists, one of 2-element 
   non-s clusters and another with 2-element s-clusters. The s-cluster list can 
   come with a caveat, e.g. These may be appropriate targets if they are NOT 
   error-patterning like  /st-/, /sp-/, /sk-/ (violate SSP, are adjunct clusters).
   Also, this may be difficult, but if it could spit out a summary table of 
   errors for the s/STOP/ clusters and the other s-clusters in questions.  
   If they show the same reduction pattern, then do it. If one reduces to C1 
   and the other doesn't then they aren't patterning the same. (P1/P2)
   
   TODO: Ensure affricates are not being considered in the sonority distance
   calculations.
   
   TODO: allow for menu of functions, so that user doesn't just have
   to do PATT, but could just get a minimal pair count, for example. (P4)
   
   TODO: output results into a CSV file. Include PATT references if you do. (P1/P2)
   8/11/16 - got something cool working without Phon library. If you have a groovy
   List, say row1 = ["cell1", "cell2", "cell3,with,commas"] then you can write a CSV
   kind of like this:
   import au.com.bytecode.opencsv.CSVWriter
   w = new CSVWriter(new FileWriter("raystest.csv"))
   w.writeNext(row1 as String[])
   w.close()
   
   That's it. You can probably incorporate a file menu of some sort.

   8/29/16 - got phonetic inventory printing into CSV as a kind of test around
   line 147. This is similar to how it should output when finished, by place and
   manner, etc., but it needs to be coded properly within the whole system. It will
   be nice to see if there is a standard "Save As" menu within java swing package or
   something that connects to mac interface. Try java.awt since that may work better
   for OSX.

   
   TODO: Allow for this to work in other languages besides English. For example,
   in Spanish, could have clusters that are chosen that exist in both English
   and Spanish. P1 - consider duplicate file if too much trouble reorganizing code.
   
   TODO: maybe ensure that only the client's data is getting picked up? Or 
   maybe just have this menu in case there are participants, and default would
   be to just get all records.
   3/24/16 - got option pane w/participant names for session. However,
   this might be annoying to have to choose participants for _every_ session.
   In any case, next step is to store whatever user chose, and make sure to
   filter out only records that match this speaker. 
   8/7/16 - ideally we will want to have the user able to choose who they are
   getting the inventory for, but if that will take too much time, just have it
   coded for one person. (P3)
   
   TODO: Try to understand how this could have been implemented with closures.
   */
