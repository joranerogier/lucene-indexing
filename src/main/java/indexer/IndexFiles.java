package indexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {

    private IndexFiles() {
    }

    /**
     * Index all text files under a directory.
     */
    public static void main(String[] args) throws IOException {
        String usage = "java org.apache.lucene.demo.indexer.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-metadata METADATA_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with searcher.SearchFiles";
        String indexPath = "index";
        String docsPath = null;
        String metadataPath = null;
        ;
        boolean create = true;
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i + 1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            } else if ("-metadata".equals(args[i])) {
                metadataPath = args[i + 1];
                i++;
            }
        }

        if (docsPath == null || metadataPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        // parse metadata file, a json file
        byte[] jsonData = Files.readAllBytes(Paths.get(metadataPath));
        String s = new String(jsonData);
        ObjectMapper objectMapper = new ObjectMapper();

        // json to object
        MetaField[] metaFields = objectMapper.readValue(s, MetaField[].class);

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            for (MetaField field : metaFields) {
                indexDoc(writer, field, Paths.get(docsPath, field.getRecno()));
            }

            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //
            // writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");
        } catch (
                IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }

    }

    /**
     * Indexes a single document
     */
    static void indexDoc(IndexWriter writer, MetaField fields, Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // make a new, empty document
            Document doc = new Document();

            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);

            // Add the pagerank of the file to a FeatureField named feature with feature name pagerank
            // For testing this feature, assign use the document id as pagerank
            Float pagerankScore = fields.getPagerank();
            if (pagerankScore == 0.0f) {
                pagerankScore = 0.000001f;
            }
            FeatureField pagerank = new FeatureField("feature", "pagerank", pagerankScore);
            doc.add(pagerank);

            // Add the URL of the file to a field name TextField with name url.
            Field url = new TextField("url", fields.getUrl(), Field.Store.YES);
            doc.add(url);

            // Add the title of the file as a TextField, makes it searchable
            String titleText = fields.getTitle();
            if (titleText == null) {
                titleText = fields.getUrl();
            }
            Field title = new TextField("title", titleText, Field.Store.YES);
            doc.add(title);

            long lastModified = Files.getLastModifiedTime(file).toMillis();
            doc.add(new LongPoint("modified", lastModified));

            BufferedReader br = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
            StringBuffer sb = new StringBuffer();
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                sb.append(sCurrentLine);
            }
            doc.add(new TextField("contents", sb.toString(), Field.Store.YES));

            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                System.out.println("updating tr" + file);
                writer.updateDocument(new Term("path", file.toString()), doc);
            }
        } catch (NoSuchFileException e) {
            System.out.println("File not found" + e.getMessage());
        }
    }
}
