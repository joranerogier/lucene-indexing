import org.apache.lucene.search.ScoreDoc;

import java.util.List;

public class Query_Hit {

    private String expanded_query;
    private List<Hit> hits;

    public Query_Hit(String expanded_query, List<Hit> hits) {
        this.hits = hits;
        this.expanded_query = expanded_query;
    }

    // getters required for jackson (json conversion) to work

    public List<Hit> getHits() {
        return hits;
    }

    public String getExpanded_query(){ return expanded_query; }
}

