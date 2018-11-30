package queryExpansion;

import org.apache.lucene.search.BoostQuery;

import java.util.Comparator;


public class QueryBoostComparator implements Comparator {
    public QueryBoostComparator() {

    }

    public int compare(Object obj1, Object obj2) {
        BoostQuery q1 = (BoostQuery) obj1;
        BoostQuery q2 = (BoostQuery) obj2;
        if (q1.getBoost() > q2.getBoost())
            return -1;
        else if (q1.getBoost() < q2.getBoost())
            return 1;
        else
            return 0;
    }
}
