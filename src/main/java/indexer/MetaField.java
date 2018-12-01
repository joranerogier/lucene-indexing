package indexer;

/**
 * Represents the metadata associated with a document.
 */
public class MetaField {
    private float pagerank;
    private String url;
    private String title;
    private String recno;
    private String outdegree;

    public MetaField() {
    }

    public float getPagerank() {
        return pagerank;
    }

    public void setPagerank(float pagerank) {
        this.pagerank = pagerank;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRecno() {
        return recno;
    }

    public String getOutdegree() {
        return outdegree;
    }

    @Override
    public String toString() {
        return "{" +
                "Recno:" + recno +
                ", Url:" + url +
                ", title:" + title +
                ", Pagerank:" + pagerank +
                '}';
    }
}