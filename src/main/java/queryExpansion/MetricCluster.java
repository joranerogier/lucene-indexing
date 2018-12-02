package queryExpansion;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

public class MetricCluster {
    IndexSearcher searcher;
    Analyzer analyzer;
    Query query;
    ScoreDoc[] hits;
    Vector<String> vocab;

    public MetricCluster(IndexSearcher searcher, Analyzer analyzer) {
        this.analyzer = analyzer;
        this.searcher = searcher;
        this.vocab = new Vector<String>();
    }

    public Query localCluster(Query query, ScoreDoc[] hits) throws IOException {
        Vector<Document> local_docs = new Vector<Document>();
        if(hits.length>=10)
        {
            for (int i = 0; i < 10; i++) {
                local_docs.add(searcher.doc(hits[i].doc));
            }
        }
        else {
            for (int i = 0; i < hits.length; i++) {
                local_docs.add(searcher.doc(hits[i].doc));
            }
        }
        Vector<QueryDoc> DocVector = convert_to_doc_vector(local_docs);
        Document doc = new Document();
        String queryString = query.toString();
        String[] split_string = queryString.split("contents:");
        String final_query_string = String.join("", split_string);
        doc.add(new TextField("contents", final_query_string, Field.Store.YES));
        Vector<Document> querydocvec = new Vector<Document>();
        querydocvec.add(doc);
        Vector<QueryDoc> queryVector = convert_to_doc_vector(querydocvec);
        Query expandedQuery = get_expanded_query(queryVector.elementAt(0), DocVector);
        return expandedQuery;
    }

    public Vector<QueryDoc> convert_to_doc_vector(Vector<Document> local_docs) throws IOException {
        Vector<QueryDoc> queryDocs = new Vector<QueryDoc>();
        for (int i = 0; i < local_docs.size(); i++) {
            Document doc = local_docs.elementAt(i);
            QueryDoc qd = new QueryDoc();
            qd.stems = get_stems_from_document(doc);
            queryDocs.add(qd);
        }
        return queryDocs;
    }

    public HashMap<String, Integer> get_stems_from_document(Document doc) throws IOException {
        QueryParser parser = new QueryParser("contents", analyzer);
        HashMap<String, Integer> stems = new HashMap<String, Integer>();
        String queryString = doc.get("contents");
        TokenStream stream = analyzer.tokenStream("contents", new StringReader(queryString));
        if (stream != null) {
            stream.reset();
            OffsetAttribute offsetAttribute = stream.getAttribute(OffsetAttribute.class);
            CharTermAttribute termAttribute = stream.getAttribute(CharTermAttribute.class);
            List<String> terms = new ArrayList<String>();
            boolean hasMoreTokens = false;
            try {
                while (stream.incrementToken()) {
                    int startOffset = offsetAttribute.startOffset();
                    int endOffset = offsetAttribute.endOffset();
                    terms.add(termAttribute.toString());
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            stream.reset();
            Stemmer stemmer = new Stemmer();
            int position = 0;
            for (int i = 0; i < terms.size(); i++) {
                stemmer.add(terms.get(i).toCharArray(), terms.get(i).length());
                stemmer.stem();
                if (stems.containsKey(terms.get(i))) {
                    //do nothing, taking the smallest possible position
                } else {
                    if (!this.vocab.contains(terms.get(i))&&!terms.get(i).chars().anyMatch(Character::isDigit)) {
                        this.vocab.add(terms.get(i));
                    }
                    if(!terms.get(i).chars().anyMatch(Character::isDigit))
                    {
                        stems.put(terms.get(i), ++position);
                    }

                }
            }
            return stems;
        } else {
            throw new IOException();
        }

    }


    public Query get_expanded_query(QueryDoc queryDoc, Vector<QueryDoc> docVector) throws IOException {

        HashMap<String, ClusterStructure> mapped_cluster_structure = new HashMap<String, ClusterStructure>();
        HashMap<String, ClusterStructure> mapped_cluster_structure_norm = new HashMap<String, ClusterStructure>();

        for (int j = 0; j < docVector.size(); j++) {

            Iterator it = queryDoc.stems.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                for (int l = 0; l < vocab.size(); l++) {
                    if (docVector.elementAt(j).stems.containsKey(this.vocab.elementAt(l)) && docVector.elementAt(j).stems.containsKey(pair.getKey())) {
                        float distance = Math.abs((docVector.elementAt(j).stems.get(pair.getKey()) - docVector.elementAt(j).stems.get(this.vocab.elementAt(l))));
                        if (!mapped_cluster_structure.containsKey(pair.getKey() + "," + this.vocab.elementAt(l)) && !mapped_cluster_structure.containsKey(this.vocab.elementAt(l) + "," + pair.getKey())) {
                            ClusterStructure cs = new ClusterStructure();
                            cs.query_term = (String) pair.getKey();
                            cs.co_term = this.vocab.elementAt(l);
                            if (distance != 0) {
                                cs.value = 1 / distance;
                            } else cs.value = 0;
                            mapped_cluster_structure.put((pair.getKey()) + "," + this.vocab.elementAt(l), cs);
                        } else {
                            ClusterStructure cs = mapped_cluster_structure.get((pair.getKey()) + "," + this.vocab.elementAt(l));
                            if (cs != null) {
                                if (distance != 0) {
                                    cs.value += 1 / distance;
                                }
                            } else {
                                ClusterStructure cs1 = mapped_cluster_structure.get(this.vocab.elementAt(l) + "," + (pair.getKey()));
                                if (cs1 != null) {
                                    if (distance != 0) {
                                        cs1.value += 1 / distance;
                                    }
                                }

                            }

                        }
                    }
                }
            }

        }

        for (int j = 0; j < docVector.size(); j++) {
            Iterator it = queryDoc.stems.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                for (int l = 0; l < vocab.size(); l++) {
                    if (docVector.elementAt(j).stems.containsKey(this.vocab.elementAt(l)) && docVector.elementAt(j).stems.containsKey(pair.getKey())) {
                        if (mapped_cluster_structure.containsKey(pair.getKey() + "," + this.vocab.elementAt(l))) {
                            float cluster_sum = get_count((String) pair.getKey(), docVector.elementAt(j)) + get_count(this.vocab.elementAt(l), docVector.elementAt(j));
                            float normalized_value = mapped_cluster_structure.get(pair.getKey() + "," + this.vocab.elementAt(l)).value / cluster_sum;
                            if (!mapped_cluster_structure_norm.containsKey(pair.getKey() + "," + this.vocab.elementAt(l))) {
                                ClusterStructure cs = new ClusterStructure();
                                cs.query_term = (String) pair.getKey();
                                cs.co_term = this.vocab.elementAt(l);
                                cs.value = normalized_value;
                                mapped_cluster_structure_norm.put((pair.getKey()) + "," + this.vocab.elementAt(l), cs);
                            }

                        }

                    }
                }

            }
        }

        Comparator<Map.Entry<String, ClusterStructure>> Cluster_Struct_Comparator = new Comparator<Map.Entry<String, ClusterStructure>>() {
            @Override
            public int compare(Map.Entry<String, ClusterStructure> o1, Map.Entry<String, ClusterStructure> o2) {
                ClusterStructure obj1 = o1.getValue();
                ClusterStructure obj2 = o2.getValue();
                if (obj1.value > obj2.value)
                    return -1;
                else if (obj2.value > obj1.value)
                    return 1;
                else
                    return 0;
            }
        };

        Vector<String> stopwords = new Vector<String>();
        String[] stops ={"a", "as", "able", "about", "above", "according", "accordingly", "across", "actually", "after", "afterwards", "again", "against", "aint", "all", "allow", "allows", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "amongst", "an", "and", "another", "any", "anybody", "anyhow", "anyone", "anything", "anyway", "anyways", "anywhere", "apart", "appear", "appreciate", "appropriate", "are", "arent", "around", "as", "aside", "ask", "asking", "associated", "at", "available", "away", "awfully", "be", "became", "because", "become", "becomes", "becoming", "been", "before", "beforehand", "behind", "being", "believe", "below", "beside", "besides", "best", "better", "between", "beyond", "both", "brief", "but", "by", "cmon", "cs", "came", "can", "cant", "cannot", "cant", "cause", "causes", "certain", "certainly", "changes", "clearly", "co", "com", "come", "comes", "concerning", "consequently", "consider", "considering", "contain", "containing", "contains", "corresponding", "could", "couldnt", "course", "currently", "definitely", "described", "despite", "did", "didnt", "different", "do", "does", "doesnt", "doing", "dont", "done", "down", "downwards", "during", "each", "edu", "eg", "eight", "either", "else", "elsewhere", "enough", "entirely", "especially", "et", "etc", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "ex", "exactly", "example", "except", "far", "few", "ff", "fifth", "first", "five", "followed", "following", "follows", "for", "former", "formerly", "forth", "four", "from", "further", "furthermore", "get", "gets", "getting", "given", "gives", "go", "goes", "going", "gone", "got", "gotten", "greetings", "had", "hadnt", "happens", "hardly", "has", "hasnt", "have", "havent", "having", "he", "hes", "hello", "help", "hence", "her", "here", "heres", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "hi", "him", "himself", "his", "hither", "hopefully", "how", "howbeit", "however", "i", "id", "ill", "im", "ive", "ie", "if", "ignored", "immediate", "in", "inasmuch", "inc", "indeed", "indicate", "indicated", "indicates", "inner", "insofar", "instead", "into", "inward", "is", "isnt", "it", "itd", "itll", "its", "its", "itself", "just", "keep", "keeps", "kept", "know", "knows", "known", "last", "lately", "later", "latter", "latterly", "least", "less", "lest", "let", "lets", "like", "liked", "likely", "little", "look", "looking", "looks", "ltd", "mainly", "many", "may", "maybe", "me", "mean", "meanwhile", "merely", "might", "more", "moreover", "most", "mostly", "much", "must", "my", "myself", "name", "namely", "nd", "near", "nearly", "necessary", "need", "needs", "neither", "never", "nevertheless", "new", "next", "nine", "no", "nobody", "non", "none", "noone", "nor", "normally", "not", "nothing", "novel", "now", "nowhere", "obviously", "of", "off", "often", "oh", "ok", "okay", "old", "on", "once", "one", "ones", "only", "onto", "or", "other", "others", "otherwise", "ought", "our", "ours", "ourselves", "out", "outside", "over", "overall", "own", "particular", "particularly", "per", "perhaps", "placed", "please", "plus", "possible", "presumably", "probably", "provides", "que", "quite", "qv", "rather", "rd", "re", "really", "reasonably", "regarding", "regardless", "regards", "relatively", "respectively", "right", "said", "same", "saw", "say", "saying", "says", "second", "secondly", "see", "seeing", "seem", "seemed", "seeming", "seems", "seen", "self", "selves", "sensible", "sent", "serious", "seriously", "seven", "several", "shall", "she", "should", "shouldnt", "since", "six", "so", "some", "somebody", "somehow", "someone", "something", "sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry", "specified", "specify", "specifying", "still", "sub", "such", "sup", "sure", "ts", "take", "taken", "tell", "tends", "th", "than", "thank", "thanks", "thanx", "that", "thats", "thats", "the", "their", "theirs", "them", "themselves", "then", "thence", "there", "theres", "thereafter", "thereby", "therefore", "therein", "theres", "thereupon", "these", "they", "theyd", "theyll", "theyre", "theyve", "think", "third", "this", "thorough", "thoroughly", "those", "though", "three", "through", "throughout", "thru", "thus", "to", "together", "too", "took", "toward", "towards", "tried", "tries", "truly", "try", "trying", "twice", "two", "un", "under", "unfortunately", "unless", "unlikely", "until", "unto", "up", "upon", "us", "use", "used", "useful", "uses", "using", "usually", "value", "various", "very", "via", "viz", "vs", "want", "wants", "was", "wasnt", "way", "we", "wed", "well", "were", "weve", "welcome", "well", "went", "were", "werent", "what", "whats", "whatever", "when", "whence", "whenever", "where", "wheres", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "whither", "who", "whos", "whoever", "whole", "whom", "whose", "why", "will", "willing", "wish", "with", "within", "without", "wont", "wonder", "would", "would", "wouldnt", "yes", "yet", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves", "zero"};
        for(int i=0;i<stops.length;i++)
        {
            stopwords.add(stops[i]);
        }
        LinkedHashMap<String, ClusterStructure> sorted_cluster_structure_map_norm;
        sorted_cluster_structure_map_norm = mapped_cluster_structure_norm.entrySet().stream().sorted(Cluster_Struct_Comparator).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        Iterator it = queryDoc.stems.entrySet().iterator();
        Vector<String> expanded_terms = new Vector<String>();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            int count = 0;
            Iterator ti = sorted_cluster_structure_map_norm.entrySet().iterator();
            while (ti.hasNext()) {
                Map.Entry cs_pair = (Map.Entry) ti.next();
                if ((!(cs_pair.getKey().equals(pair.getKey() + "," + pair.getKey()))) && ((String) cs_pair.getKey()).split(",")[0].equals(pair.getKey())) {
                    if (count < 4) {
                        count++;
                        String pair_string = (String) cs_pair.getKey();
                        String[] values = pair_string.split(",");
                        if (!expanded_terms.contains(values[1])&&!stopwords.contains(values[1])) {
                            expanded_terms.add(values[1]);
                        } else {
                            count--;
                        }
                    } else {
                        break;
                    }
                }
            }
        }

        it = queryDoc.stems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (!expanded_terms.contains(pair.getKey())) {
                expanded_terms.add((String) pair.getKey());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expanded_terms.size(); i++) {
            sb.append(expanded_terms.elementAt(i) + " ");
        }

        Query expandedQuery = null;
        try {
            expandedQuery = new QueryParser("contents", analyzer).parse(sb.toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return expandedQuery;
    }

    public int get_count(String key, QueryDoc doc) {
        int freq = 0;
        for (Map.Entry<String, Integer> entry :
                doc.stems.entrySet()) {
            if (key.equals(entry.getKey())) {
                freq++;
            }
        }
        return freq;
    }
}

