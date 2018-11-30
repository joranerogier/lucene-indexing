package searcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import queryExpansion.AssociationCluster;
import queryExpansion.MetricCluster;
import queryExpansion.QueryExpansion;
import queryExpansion.QueryTermVector;

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
    final String fieldName = "contents";    // the field to search for
    final int MAX_RESULTS = 100;
    final int MAX_EXPANDED_QUERY_TERM_COUNT = 20;
    public String currentQuery;
    ObjectMapper jsonMapper;    // to convert to json

    public Search(String pathToIndex) throws IOException {
        reader = DirectoryReader.open(FSDirectory.open(Paths.get(pathToIndex)));
        searcher = new IndexSearcher(reader);
        analyzer = new StandardAnalyzer();
        parser = new QueryParser(fieldName, analyzer);
        this.jsonMapper = new ObjectMapper();
    }

    public static void main(String[] args) throws IOException {
        Search search = new Search("index");
        boolean usePageRank = true;
        String queryString = "similarity";
        String expansionMethod = "Metric";  // other options "Rochio", "Association", "None"
        System.out.println(search.queryIndex(queryString, usePageRank, expansionMethod));
        search.close();
    }

    private String createExpandedQuery(Query query) {
        String[] expandedQueryString = query.toString().split("contents:");
        StringBuilder sb = new StringBuilder();
        Vector<String> sbTerms = new Vector<String>();
        int count = 0;
        for (int i = 0; i < expandedQueryString.length; i++) {
            if (!sbTerms.contains(expandedQueryString[i]) &&
                    !expandedQueryString[i].chars().anyMatch(Character::isDigit) &&
                    count < MAX_EXPANDED_QUERY_TERM_COUNT) {
                count++;
                sbTerms.add(expandedQueryString[i]);
                sb.append(expandedQueryString[i]);
            }
        }
        return sb.toString();
    }

    private QueryHit getHits(Query query, boolean usePageRank, String expansionMethod) throws IOException {
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

        // for query expansion, work with the results of regular search
        results = searcher.search(query, MAX_RESULTS);
        hits = results.scoreDocs;
        String finalExpandedQueryString = "";

        // case insensitive
        if (expansionMethod.toUpperCase().equals("NONE")) {
            // no change to query
            finalExpandedQueryString = "";
        } else if (expansionMethod.toUpperCase().equals("ROCHIO")) {
            TFIDFSimilarity similarity = null;
            searcher.setSimilarity(new ClassicSimilarity());
            similarity = (TFIDFSimilarity) searcher.getSimilarity(true);
            QueryTermVector queryTermVector = new QueryTermVector(this.currentQuery, analyzer);
            QueryExpansion queryExpansion = new QueryExpansion(analyzer, searcher, similarity);
            query = queryExpansion.expandQuery(this.currentQuery, hits);
            finalExpandedQueryString = createExpandedQuery(query);
        } else if (expansionMethod.toUpperCase().equals("ASSOCIATION")) {
            AssociationCluster queryExpansion = new AssociationCluster(searcher, analyzer);
            query = queryExpansion.localCluster(query, hits);
            finalExpandedQueryString = createExpandedQuery(query);
        } else if (expansionMethod.toUpperCase().equals("METRIC")) {
            MetricCluster queryExpansion = new MetricCluster(searcher, analyzer);
            query = queryExpansion.localCluster(query, hits);
            finalExpandedQueryString = createExpandedQuery(query);
        } else {
            // no change to query
            finalExpandedQueryString = "";
        }

        if (!"".equals(finalExpandedQueryString)) {
            // search again with expanded query and return the new results
            results = searcher.search(query, MAX_RESULTS);
            hits = results.scoreDocs;
        }

        numTotalHits = Math.min(Math.toIntExact(results.totalHits), MAX_RESULTS);  // actually these many hits
        List<Hit> resultHits = new ArrayList<>();
        for (int i = 0; i < numTotalHits; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("path");
            String title = doc.get("title");    // will be null if title doesn't exist
            String url = doc.get("url");        // will be null if url doesn't exist
            resultHits.add(new Hit(path, title, url));
        }
        QueryHit queryHit = new QueryHit(finalExpandedQueryString, resultHits);
        return queryHit;
    }

    /**
     * Client should call this to get results in json format.
     *
     * @param queryString the actual query entered by the user
     * @param usePageRank whether to use pagerank or not
     * @return results in json format
     */
    public String queryIndex(String queryString, boolean usePageRank, String expansionMethod) {
        try {
            this.currentQuery = queryString;
            Query query = parser.parse(queryString);
            QueryHit results = this.getHits(query, usePageRank, expansionMethod);
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

}
