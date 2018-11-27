import org.apache.lucene.search.ScoreDoc;

public class Hit {
    private String path;
    private String title;
    private String url;

    public Hit(String path, String title, String url) {
        this.path = path;
        this.title = title;
        this.url = url;
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
}
