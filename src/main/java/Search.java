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
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
    ObjectMapper jsonMapper;    // to convert to json

    final String fieldName = "contents";    // the field to search for
    final int MAX_RESULTS = 100;

    public Search(String pathToIndex) throws IOException {
        reader = DirectoryReader.open(FSDirectory.open(Paths.get(pathToIndex)));
        searcher = new IndexSearcher(reader);
        analyzer = new StandardAnalyzer();
        parser = new QueryParser(fieldName, analyzer);
        this.jsonMapper = new ObjectMapper();
    }

    private List<Hit> getHits(Query query, boolean usePageRank) throws IOException {
        TopDocs results;
        // Lucene's way of doing things, modify query to incorporate pagerank
        if (usePageRank) {
            Query boost = FeatureField.newLogQuery("feature", "pagerank", 0.2f, 4.5f);
            Query boostedQuery = new BooleanQuery.Builder()
                    .add(query, BooleanClause.Occur.MUST)
                    .add(boost, BooleanClause.Occur.SHOULD)
                    .build();
            query = boostedQuery;
        }
        results = searcher.search(query, MAX_RESULTS);
        ScoreDoc[] hits = results.scoreDocs;
        int numTotalHits = Math.toIntExact(results.totalHits);  // actually how many hits

        List<Hit> resultHits = new ArrayList<>();
        for (int i = 0; i < numTotalHits; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("path");
            String title = doc.get("title");    // will be null if title doesn't exist
            // TODO: add url in index to get urls, for testing nulls are acceptable
            String url = doc.get("url");        // will be null if url doesn't exist
            resultHits.add(new Hit(path, title, url));
        }
        return resultHits;
    }


    /**
     * Client should call this to get results in json format.
     *
     * @param queryString the actual query entered by the user
     * @param usePageRank whether to use pagerank or not
     * @return results in json format
     */
    public String queryIndex(String queryString, boolean usePageRank) {
        try {
            Query query = parser.parse(queryString);
            List<Hit> results = this.getHits(query, usePageRank);
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
        System.out.println(search.queryIndex(queryString, usePageRank));
        search.close();
    }

}
