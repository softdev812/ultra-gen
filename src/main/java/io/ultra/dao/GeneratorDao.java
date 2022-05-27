package io.ultra.dao;

import java.util.List;
import java.util.Map;

/**
 * db api
 *
 * @author Mark softdev812@gmail.com
 * @since 2018-07-24
 */
public interface GeneratorDao {
    List<Map<String, Object>> queryList(Map<String, Object> map);

    Map<String, String> queryTable(String tableName);

    List<Map<String, String>> queryColumns(String tableName);
}
