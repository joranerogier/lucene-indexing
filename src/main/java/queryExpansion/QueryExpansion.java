package queryExpansion;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.TFIDFSimilarity;

import java.io.IOException;
import java.util.*;

public class QueryExpansion {
    BoostQuery bq;
    private Properties prop;
    private Analyzer analyzer;
    private IndexSearcher searcher;
    private TFIDFSimilarity similarity;
    private Vector<BoostQuery> expandedTerms;
    private int QE_NUM_DOC = 10;
    private int QE_NUM_TERM = 100;
    private double alpha = 1;
    private double beta = 0.75;
    private float decay;


    public QueryExpansion(Analyzer analyzer, IndexSearcher searcher, TFIDFSimilarity similarity) {
        this.analyzer = analyzer;
        this.searcher = searcher;
        this.similarity = similarity;

    }

    public Query expandQuery(String queryStr, ScoreDoc[] hits)
            throws IOException {
        Vector<Document> vHits = getDocs(queryStr, hits);
        return expandQuery(queryStr, vHits);
    }

    private Vector<Document> getDocs(String query, ScoreDoc[] hits) throws IOException {
        Vector<Document> vHits = new Vector<Document>();
        int hits_len = hits.length;
        for (int i = 0; ((i < QE_NUM_DOC) && (i < hits_len)); i++) {
            vHits.add(searcher.doc(hits[i].doc));
        }
        return vHits;
    }


    public Query mergeQueries(Vector<BoostQuery> termQueries, int maxTerms) throws QueryNodeException {
        Query query = null;

        // Select only the maxTerms number of terms
        int termCount = Math.min(termQueries.size(), maxTerms);
        // Create Query String
        StringBuffer qBuf = new StringBuffer();
        for (int i = 0; i < termCount; i++) {
            TermQuery termQuery = (TermQuery) termQueries.elementAt(i).getQuery();
            Term term = termQuery.getTerm();
            qBuf.append(QueryParser.escape(term.text()).toLowerCase() + " ");

        }
        String targetStr = qBuf.toString();
        try {
            query = new QueryParser("contents", analyzer).parse(targetStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Parse StringQuery to create Query


        return query;
    }

    public Query expandQuery(String queryStr, Vector<Document> hits)
            throws IOException {
        double alpha = this.alpha;
        double beta = this.beta;
        int docNum = QE_NUM_DOC;
        int termNum = QE_NUM_TERM;
        Vector<QueryTermVector> docsTermVector = getDocsTerms(hits, docNum, analyzer);
        Query expandedQuery = adjust(docsTermVector, queryStr, alpha, beta, docNum, termNum);
        return expandedQuery;
    }


    public Vector<QueryTermVector> getDocsTerms(Vector<Document> hits, int docsRelevantCount, Analyzer analyzer)
            throws IOException {
        Vector<QueryTermVector> docsTerms = new Vector<QueryTermVector>();

        // Process each of the documents
        for (int i = 0; ((i < docsRelevantCount) && (i < hits.size())); i++) {
            Document doc = hits.elementAt(i);
            // Get text of the document and append it

            String docTxtFlds = doc.get("contents");


            // Create termVector and add it to vector
            QueryTermVector docTerms = new QueryTermVector(docTxtFlds, analyzer);
            docsTerms.add(docTerms);
        }

        return docsTerms;
    }


    public Query adjust(Vector<QueryTermVector> docsTermsVector, String queryStr, double alpha, double beta, int docRelevantCount, int maxExpandedQueryTerms)
            throws IOException {
        Query expandedQuery;
        Vector<BoostQuery> docsTerms = setBoost(docsTermsVector, beta);
        QueryTermVector queryTermVector = new QueryTermVector(queryStr, analyzer);
        Vector<BoostQuery> queryterms = setBoost(queryTermVector, alpha);
        Vector<BoostQuery> expandedQueryTerms = combine(queryterms, docsTerms);
        setExpandedTerms(expandedQueryTerms);
        Comparator comparator = new QueryBoostComparator();
        Collections.sort(expandedQueryTerms, comparator);
        expandedQuery = null;
        try {
            expandedQuery = mergeQueries(expandedQueryTerms, maxExpandedQueryTerms);
        } catch (QueryNodeException e) {
            e.printStackTrace();
        }


        return expandedQuery;
    }

    public Vector<BoostQuery> setBoost(QueryTermVector termVector, double factor)
            throws IOException {
        Vector<QueryTermVector> v = new Vector<QueryTermVector>();
        v.add(termVector);

        return setBoost(v, factor);
    }

    public Vector<BoostQuery> setBoost(Vector<QueryTermVector> docsTerms, double factor)
            throws IOException {
        Vector<BoostQuery> terms = new Vector<BoostQuery>();
        for (int g = 0; g < docsTerms.size(); g++) {
            QueryTermVector docTerms = docsTerms.elementAt(g);
            String[] termsTxt = docTerms.getTerms();
            int[] termFrequencies = docTerms.getTermFrequencies();
            for (int i = 0; i < docTerms.size(); i++) {
                String termTxt = termsTxt[i];
                Term term = new Term("", termTxt);
                float tf = termFrequencies[i];
                float idf = similarity.idf((long) tf, docsTerms.size());
                float weight = tf * idf;
                TermQuery termQuery = new TermQuery(term);
                BoostQuery boostQuery = new BoostQuery(termQuery, (float) factor * weight);
                terms.add(boostQuery);
            }
        }
        merge(terms);
        return terms;
    }

    public Vector<BoostQuery> combine(Vector<BoostQuery> queryTerms, Vector<BoostQuery> docsTerms) {
        Vector<BoostQuery> terms = new Vector<BoostQuery>();
        terms.addAll(docsTerms);
        terms.removeIf((BoostQuery bq) -> ((((TermQuery) bq.getQuery()).getTerm().text().matches("\\d+"))));
        for (int i = 0; i < queryTerms.size(); i++) {
            BoostQuery qTerm = queryTerms.elementAt(i);
            BoostQuery term = find(qTerm, terms);
            if (term != null) {
                float weight = qTerm.getBoost() + term.getBoost();
                BoostQuery boostQuery = new BoostQuery(term.getQuery(), weight);
                terms.remove(term);
                if (!((TermQuery) term.getQuery()).getTerm().text().matches("\\d+")) {
                    terms.add(boostQuery);
                }
            } else {

                terms.add(qTerm);

            }
        }

        return terms;
    }

    public BoostQuery find(BoostQuery term, Vector<BoostQuery> terms) {
        BoostQuery termF = null;
        Iterator<BoostQuery> iterator = terms.iterator();
        while (iterator.hasNext()) {
            BoostQuery currentTerm = iterator.next();
            if (((TermQuery) term.getQuery()).getTerm().text().equals(((TermQuery) currentTerm.getQuery()).getTerm().text())) {
                termF = currentTerm;
            }
        }
        return termF;
    }

    private void merge(Vector<BoostQuery> terms) {

        Vector<BoostQuery> bqterms = new Vector<BoostQuery>();
        for (int i = 0; i < terms.size(); i++) {
            BoostQuery term = terms.elementAt(i);

            float boostSum = term.getBoost();

            for (int j = i + 1; j < terms.size(); j++) {
                BoostQuery tmpTerm = terms.elementAt(j);
                if (((TermQuery) tmpTerm.getQuery()).getTerm().text().equals(((TermQuery) term.getQuery()).getTerm().text())) {
                    boostSum += tmpTerm.getBoost();
                    terms.remove(tmpTerm);
                }
            }
            BoostQuery bq = new BoostQuery(term.getQuery(), boostSum);
            if (!((TermQuery) term.getQuery()).getTerm().text().matches("\\d+")) {
                bqterms.add(bq);
            }
        }
    }

    private void setExpandedTerms(Vector<BoostQuery> expandedTerms) {
        this.expandedTerms = expandedTerms;
    }

}

