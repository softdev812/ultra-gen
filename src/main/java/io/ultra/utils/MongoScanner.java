package io.ultra.utils;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import io.ultra.config.MongoManager;
import io.ultra.entity.mongo.MongoDefinition;
import io.ultra.entity.mongo.Type;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * @author: gxz  514190950@qq.com
 **/
public class MongoScanner {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private MongoCollection<Document> collection;

    final private int scanCount;

    private List<String> colNames;

    private MongoDefinition mongoDefinition;


    private final static int[] TYPE = {3, 16, 18, 8, 9, 2, 1};

    private final static int ARRAY_TYPE = 4;

    private final static int MAX_COUNT = 200000;

    private final static int DEFAULT_COUNT = 100000;


    public MongoScanner(MongoCollection<Document> collection) {
        this.collection = collection;
        this.scanCount = DEFAULT_COUNT;
        scan();
    }

    private void scan() {
        initColNames();
        mongoDefinition = scanType();
        MongoManager.putInfo(collection.getNamespace().getCollectionName(), mongoDefinition);
        this.collection = null;

    }

    public MongoDefinition getProduct() {
        return mongoDefinition;
    }


    public List<String> groupAggregation(Integer skip, Integer limit) throws MongoCommandException {
        skip = skip == null ? 0 : skip;
        limit = limit == null ? scanCount : limit;
        MongoCollection<Document> collection = this.collection;
        BasicDBObject $project = new BasicDBObject("$project", new BasicDBObject("arrayofkeyvalue", new BasicDBObject("$objectToArray", "$$ROOT")));
        BasicDBObject $unwind = new BasicDBObject("$unwind", "$arrayofkeyvalue");
        BasicDBObject $skip = new BasicDBObject("$skip", skip);
        BasicDBObject $limit = new BasicDBObject("$limit", limit);
        BasicDBObject filed = new BasicDBObject("_id", "null");
        filed.append("allkeys", new BasicDBObject("$addToSet", "$arrayofkeyvalue.k"));
        BasicDBObject $group = new BasicDBObject("$group", filed);
        List<BasicDBObject> dbStages = Arrays.asList($project, $skip, $limit, $unwind, $group);
        AggregateIterable<Document> aggregate = collection.aggregate(dbStages);
        Document document = aggregate.first();
        if (document == null) {
            BasicDBObject existsQuery = new BasicDBObject("$ROOT", new BasicDBObject("$exists", true));
            MongoCursor<Document> existsList = collection.find(existsQuery).limit(100).iterator();
            Set<String> keySet = new HashSet<>();
            while (existsList.hasNext()) {
                Document next = existsList.next();
                Map<String, Object> keyMap = (Document) next.get("$ROOT");
                keySet.addAll(keyMap.keySet());
            }
            return new ArrayList<>(keySet);
        } else {
            return (List<String>) document.get("allkeys");
        }

    }

    public Set<String> getNextParameterNames(String parameterName) {
        Document condition = new Document(parameterName, new Document("$exists", true));
        Document match = new Document("$match", condition);
        String unwindName = parameterName;
        if (parameterName.contains(".")) {
            unwindName = parameterName.split("\\.")[0];
        }
        Document unwind = new Document("$unwind", "$" + unwindName);
        Document limit = new Document("$limit", 3000);
        Document project = new Document("$project", new Document("list", "$" + parameterName).append("_id", false));
        Document unwind2 = new Document("$unwind", "$list");
        AggregateIterable<Document> aggregate = this.collection.aggregate(Arrays.asList(match, unwind, limit, project, unwind2));
        Set<String> names = new HashSet<>();
        for (Document document : aggregate) {
            Object list = document.get("list");
            if (list instanceof Map) {
                Set<String> documentNames = ((Document) list).keySet();
                names.addAll(documentNames);
            }
        }

        return names;
    }


    public MongoDefinition processNameType(String propertyName) {
        MongoCollection<Document> collection = this.collection;
        MongoDefinition result = new MongoDefinition();
        if ("_id".equals(propertyName)) {
            result.setType(2);
            result.setPropertyName("_id");
            return result;
        }
        result.setPropertyName(propertyName);
        MongoCursor<Document> isArray = collection.find(new Document(propertyName, new Document("$type", ARRAY_TYPE))).limit(1).iterator();
        if (isArray.hasNext()) {
            result.setArray(true);
            for (int i : TYPE) {
                MongoCursor<Document> iterator = collection.find(new Document(propertyName, new Document("$type", i))).limit(1).iterator();
                if (iterator.hasNext()) {
                    if (i == 3) {
                        result.setChild(this.produceChildList(propertyName));
                    }
                    result.setType(i);
                    return result;
                }
            }
        } else {
            for (int i : TYPE) {
                MongoCursor<Document> iterator = collection.find(new Document(propertyName, new Document("$type", i))).limit(1).iterator();
                if (iterator.hasNext()) {
                    if (i == 3) {
                        result.setChild(this.produceChildList(propertyName));
                    }
                    result.setType(i);
                    return result;
                }
            }
            result.setType(2);
        }
        return result;
    }


    private List<MongoDefinition> produceChildList(String parentName) {
        Set<String> nextParameterNames = this.getNextParameterNames(parentName);
        List<String> strings = new ArrayList<>(nextParameterNames);
        List<String> collect = strings.stream().map(name -> parentName + "." + name).collect(Collectors.toList());
        ForkJoinPool pool = new ForkJoinPool();
        ForkJoinTask<List<MongoDefinition>> task = new ForkJoinProcessType(collect);
        return pool.invoke(task);
    }

    private List<String> distinctAndJoin(List<String> a, List<String> b) {
        a.removeAll(b);
        a.addAll(b);
        return a;
    }

    private void initColNames() {
        long start = System.currentTimeMillis();
        int scan = this.scanCount;
        long count = this.collection.countDocuments();
        ForkJoinPool pool = new ForkJoinPool();
        ForkJoinTask<List<String>> task;
        if (count > (long) scan) {
            task = new ForkJoinGetProcessName(0, scan);
        } else {
            task = new ForkJoinGetProcessName(0, (int) count);
        }
        this.colNames = pool.invoke(task);
    }

    private MongoDefinition scanType() {
        MongoDefinition result = new MongoDefinition();
        List<String> colNames = this.colNames;
        ForkJoinPool pool = new ForkJoinPool();
        ForkJoinTask<List<MongoDefinition>> task = new ForkJoinProcessType(colNames);
        List<MongoDefinition> invoke = pool.invoke(task);
        return result.setChild(invoke).setPropertyName(this.collection.getNamespace().getCollectionName());
    }

    class ForkJoinProcessType extends RecursiveTask<List<MongoDefinition>> {
        List<String> names;
        private final int THRESHOLD = 6;

        ForkJoinProcessType(List<String> names) {
            this.names = names;
        }

        @Override
        protected List<MongoDefinition> compute() {
            if (names.size() <= THRESHOLD) {
                List<MongoDefinition> result = new ArrayList<>();
                for (String name : names) {
                    MongoDefinition childrenDefinition = processNameType(name);
                    result.add(childrenDefinition);
                }
                return result;
            } else {
                int size = names.size();
                int middle = size / 2;
                List<String> leftList = names.subList(0, middle);
                List<String> rightList = names.subList(middle, size);
                ForkJoinProcessType pre = new ForkJoinProcessType(leftList);
                pre.fork();
                ForkJoinProcessType next = new ForkJoinProcessType(rightList);
                next.fork();
                return mergeList(pre.join(), next.join());
            }
        }
    }

    class ForkJoinGetProcessName extends RecursiveTask<List<String>> {
        private int begin;
        private int end;
        private final int THRESHOLD = 5000;

        ForkJoinGetProcessName(int begin, int end) {
            this.begin = begin;
            this.end = end;
        }

        @Override
        protected List<String> compute() {
            int count = end - begin;
            if (THRESHOLD >= count) {
                return groupAggregation(begin, count);
            } else {
                int middle = (begin + end) / 2;
                ForkJoinGetProcessName pre = new ForkJoinGetProcessName(begin, middle);
                pre.fork();
                ForkJoinGetProcessName next = new ForkJoinGetProcessName(middle + 1, end);
                next.fork();
                return distinctAndJoin(pre.join(), next.join());
            }
        }
    }
    public  <T> List<T> mergeList(List<T> list1, List<T> list2){
        list1.addAll(list2);
        return list1;
    }
}
