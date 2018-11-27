import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Implements search functionality.
 * <p>
 * Web application needs to call {@code queryIndex()} to obtain results in json format. Call {@code close()} in app after
 * you are finished with searching.
 */
public class Search {
    // static so that they are initialized once
    private static IndexReader reader;
    private static IndexSearcher searcher;
    private static Analyzer analyzer;
    private static QueryParser parser;
    public String current_query;
    ObjectMapper jsonMapper;    // to convert to json

    final String fieldName = "contents";    // the field to search for
    final int MAX_RESULTS = 100;
    final int MAX_EXPANDED_QUERY_TERM_COUNT= 20;

    public Search(String pathToIndex) throws IOException {
        reader = DirectoryReader.open(FSDirectory.open(Paths.get(pathToIndex)));
        searcher = new IndexSearcher(reader);
        analyzer = new StandardAnalyzer();
        parser = new QueryParser(fieldName, analyzer);
        this.jsonMapper = new ObjectMapper();
    }

    private Query_Hit getHits(Query query, boolean usePageRank,String expansion_Method) throws IOException {
        TopDocs results;
        ScoreDoc[] hits;
        int numTotalHits;
        // Lucene's way of doing things, modify query to incorporate pagerank
        if (usePageRank) {
            Query boost = FeatureField.newLogQuery("feature", "pagerank", 0.2f, 4.5f);
            Query boostedQuery = new BooleanQuery.Builder()
                    .add(query, BooleanClause.Occur.MUST)
                    .add(boost, BooleanClause.Occur.SHOULD)
                    .build();
            query = boostedQuery;
        }
         // actually how many hits

        //case insensitive
        if(expansion_Method.toUpperCase().equals("NONE"))
        {
            results = searcher.search(query, MAX_RESULTS);
            hits = results.scoreDocs;
            numTotalHits = Math.toIntExact(results.totalHits);
            List<Hit> resultHits = new ArrayList<>();
            for (int i = 0; i < numTotalHits; i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                String title = doc.get("title");    // will be null if title doesn't exist
                // TODO: add url in index to get urls, for testing nulls are acceptable
                String url = doc.get("url");        // will be null if url doesn't exist
                resultHits.add(new Hit(path, title, url));
            }
            Query_Hit query_hit = new Query_Hit("",resultHits);
            return query_hit;
        }
        else if(expansion_Method.toUpperCase().equals("ROCHIO"))
        {
            results = searcher.search(query, MAX_RESULTS);
            hits = results.scoreDocs;
            List<Hit> resultHits = new ArrayList<>();
            TFIDFSimilarity similarity = null;
            searcher.setSimilarity(new ClassicSimilarity());
            similarity =(TFIDFSimilarity)searcher.getSimilarity(true);
            QueryTermVector queryTermVector = new QueryTermVector( this.current_query, analyzer );
            QueryExpansion queryExpansion = new QueryExpansion(analyzer,searcher,similarity);
            query = queryExpansion.expandQuery(this.current_query,hits);
            String[] expanded_query_string = query.toString().split("contents:");
            StringBuilder sb = new StringBuilder();
            Vector<String> sb_terms = new Vector<String>();
            int count =0;
            for(int i=0;i<expanded_query_string.length;i++)
            {
                if(!sb_terms.contains(expanded_query_string[i])&& !expanded_query_string[i].chars().anyMatch(Character::isDigit)&&count<MAX_EXPANDED_QUERY_TERM_COUNT)
                {
                    count++;
                    sb_terms.add(expanded_query_string[i]);
                    sb.append(expanded_query_string[i]);
                }
            }
            String final_expanded_string = sb.toString();
            results = searcher.search(query,MAX_RESULTS);
            hits = results.scoreDocs;


            for (int i = 0; i < hits.length; i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                String title = doc.get("title");    // will be null if title doesn't exist
                // TODO: add url in index to get urls, for testing nulls are acceptable
                String url = doc.get("url");        // will be null if url doesn't exist
                resultHits.add(new Hit(path, title, url));
            }
            Query_Hit query_hit = new Query_Hit(final_expanded_string,resultHits);
            return query_hit;

        }
        else if(expansion_Method.toUpperCase().equals("ASSOCIATION"))
        {
            results = searcher.search(query, MAX_RESULTS);
            hits = results.scoreDocs;
            List<Hit> resultHits = new ArrayList<>();
            AssociationCluster queryExpansion = new AssociationCluster(searcher,analyzer);
            query = queryExpansion.local_cluster(query,hits);
            String[] expanded_query_string = query.toString().split("contents:");
            StringBuilder sb = new StringBuilder();
            Vector<String> sb_terms = new Vector<String>();
            int count =0;
            for(int i=0;i<expanded_query_string.length;i++)
            {
                if(!sb_terms.contains(expanded_query_string[i])&& !expanded_query_string[i].chars().anyMatch(Character::isDigit)&&count<MAX_EXPANDED_QUERY_TERM_COUNT)
                {
                    count++;
                    sb_terms.add(expanded_query_string[i]);
                    sb.append(expanded_query_string[i]);
                }
            }
            String final_expanded_string = sb.toString();
            results = searcher.search(query,MAX_RESULTS);
            hits = results.scoreDocs;


            for (int i = 0; i < hits.length; i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                String title = doc.get("title");    // will be null if title doesn't exist
                // TODO: add url in index to get urls, for testing nulls are acceptable
                String url = doc.get("url");        // will be null if url doesn't exist
                resultHits.add(new Hit(path, title, url));
            }
            Query_Hit query_hit = new Query_Hit(final_expanded_string,resultHits);
            return query_hit;

        }
        else if(expansion_Method.toUpperCase().equals("METRIC"))
        {
            results = searcher.search(query, MAX_RESULTS);
            hits = results.scoreDocs;
            List<Hit> resultHits = new ArrayList<>();
            MetricCluster queryExpansion = new MetricCluster(searcher,analyzer);
            query = queryExpansion.local_cluster(query,hits);
            String[] expanded_query_string = query.toString().split("contents:");
            StringBuilder sb = new StringBuilder();
            Vector<String> sb_terms = new Vector<String>();
            int count =0;
            for(int i=0;i<expanded_query_string.length;i++)
            {
                if(!sb_terms.contains(expanded_query_string[i])&& !expanded_query_string[i].chars().anyMatch(Character::isDigit)&&count<MAX_EXPANDED_QUERY_TERM_COUNT)
                {
                    count++;
                    sb_terms.add(expanded_query_string[i]);
                    sb.append(expanded_query_string[i]);
                }
            }
            String final_expanded_string = sb.toString();
            results = searcher.search(query,MAX_RESULTS);
            hits = results.scoreDocs;


            for (int i = 0; i < hits.length; i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                String title = doc.get("title");    // will be null if title doesn't exist
                // TODO: add url in index to get urls, for testing nulls are acceptable
                String url = doc.get("url");        // will be null if url doesn't exist
                resultHits.add(new Hit(path, title, url));
            }
            Query_Hit query_hit = new Query_Hit(final_expanded_string,resultHits);
            return query_hit;
        }
        else {

            results = searcher.search(query, MAX_RESULTS);
            hits = results.scoreDocs;
            numTotalHits = Math.toIntExact(results.totalHits);
            List<Hit> resultHits = new ArrayList<>();
            for (int i = 0; i < numTotalHits; i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                String title = doc.get("title");    // will be null if title doesn't exist
                // TODO: add url in index to get urls, for testing nulls are acceptable
                String url = doc.get("url");        // will be null if url doesn't exist
                resultHits.add(new Hit(path, title, url));
            }
            Query_Hit query_hit = new Query_Hit("",resultHits);
            return query_hit;
        }


    }


    /**
     * Client should call this to get results in json format.
     *
     * @param queryString the actual query entered by the user
     * @param usePageRank whether to use pagerank or not
     * @return results in json format
     */
    public String queryIndex(String queryString, boolean usePageRank,String expansion_method) {
        try {
            this.current_query = queryString;
            Query query = parser.parse(queryString);
            Query_Hit results = this.getHits(query, usePageRank,expansion_method);
            return this.jsonMapper.writeValueAsString(results);
        } catch (IOException e) {
            return "ERROR with reading index, IOException";
        } catch (ParseException e) {
            return "ERROR with parsing query, ParseException";
        }
    }

    /**
     * Call when no need to search anymore.
     *
     * @throws IOException if there is a low-level IO error
     */
    public void close() throws IOException {
        reader.close();
    }

    public static void main(String args[]) throws IOException {
        Search search = new Search("index");
        boolean usePageRank = true;
        String queryString = "similarity";
//        String expansion_method = "Rochio";
//        String expansion_method = "Association";
        String expansion_method = "Metric";
//        String expansion_method = "None";

        System.out.println(search.queryIndex(queryString, usePageRank, expansion_method));
        search.close();
    }

}
