import org.apache.lucene.search.ScoreDoc;

public class Query_Hit {
    private String path;
    private String title;
    private String url;
    private String expanded_query;

    public Query_Hit(String path, String title, String url,String expanded_query) {
        this.path = path;
        this.title = title;
        this.url = url;
        this.expanded_query = expanded_query;
    }

    // getters required for jackson (json conversion) to work
    public String getPath() {
        return path;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String expandedQuery(){return expanded_query;}
}

