package org.verdictdb.core.execution;

import org.junit.BeforeClass;
import org.junit.Test;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.connection.JdbcConnection;
import org.verdictdb.core.query.SelectQuery;
import org.verdictdb.core.sql.NonValidatingSQLParser;
import org.verdictdb.exception.VerdictDBDbmsException;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.sql.syntax.H2Syntax;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QueryExecutionPlanCompressTest {

  static String originalSchema = "originalschema";

  static String originalTable = "originaltable";

  static String newSchema = "newschema";

  static String newTable  = "newtable";

  static int aggblockCount = 2;

  static DbmsConnection conn;

  @BeforeClass
  public static void setupDbConnAndScrambledTable() throws SQLException, VerdictDBException {
    final String DB_CONNECTION = "jdbc:h2:mem:createasselecttest;DB_CLOSE_DELAY=-1";
    final String DB_USER = "";
    final String DB_PASSWORD = "";
    conn = new JdbcConnection(DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD), new H2Syntax());
    conn.executeUpdate(String.format("CREATE SCHEMA IF NOT EXISTS\"%s\"", originalSchema));
    conn.executeUpdate(String.format("CREATE SCHEMA IF NOT EXISTS\"%s\"", newSchema));
    populateData(conn, originalSchema, originalTable);
  }

  static void populateData(DbmsConnection conn, String schemaName, String tableName) throws VerdictDBDbmsException {
    conn.executeUpdate(String.format("CREATE TABLE \"%s\".\"%s\"(\"id\" int, \"value\" double)", schemaName, tableName));
    for (int i = 0; i < 10; i++) {
      conn.executeUpdate(String.format("INSERT INTO \"%s\".\"%s\"(\"id\", \"value\") VALUES(%s, %f)",
          schemaName, tableName, i, (double) i+1));
    }
  }

  //@Test
  public void simpleAggregateTest() throws VerdictDBException {
    String sql = "select avg(t.value) as a from originalschema.originaltable as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    queryExecutionPlan.compress();
    assertEquals(0, queryExecutionPlan.root.dependents.size());
    assertEquals(selectQuery, queryExecutionPlan.root.selectQuery.getFromList().get(0));

    //queryExecutionPlan.root.execute(conn);
  }

  @Test
  public void NestedAggregateFromTest() throws VerdictDBException {
    String sql = "select avg(t.value) from (select o.value from originalschema.originaltable as o where o.value>5) as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    queryExecutionPlan.compress();
    assertEquals(0, queryExecutionPlan.root.dependents.size());
    assertEquals(selectQuery, queryExecutionPlan.root.selectQuery.getFromList().get(0));

    queryExecutionPlan.root.execute(conn);
  }
}
