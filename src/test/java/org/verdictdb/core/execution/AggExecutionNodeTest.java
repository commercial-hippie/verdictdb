package org.verdictdb.core.execution;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.connection.DbmsQueryResult;
import org.verdictdb.connection.JdbcConnection;
import org.verdictdb.core.query.AliasedColumn;
import org.verdictdb.core.query.BaseColumn;
import org.verdictdb.core.query.BaseTable;
import org.verdictdb.core.query.ColumnOp;
import org.verdictdb.core.query.CreateTableAsSelect;
import org.verdictdb.core.query.SelectItem;
import org.verdictdb.core.query.SelectQueryOp;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.core.rewriter.ScrambleMetaForTable;
import org.verdictdb.core.scramble.UniformScrambler;
import org.verdictdb.core.sql.CreateTableToSql;
import org.verdictdb.exception.VerdictDbException;
import org.verdictdb.sql.syntax.H2Syntax;
import org.verdictdb.sql.syntax.HiveSyntax;

public class AggExecutionNodeTest {
  
  static Connection conn;
  
  static Statement stmt;
  
  int aggblockCount = 2;
  
  static ScrambleMeta meta = new ScrambleMeta();
  
  static String scrambledTable;
  
  @BeforeClass
  public static void setupH2Database() throws SQLException, VerdictDbException {
    final String DB_CONNECTION = "jdbc:h2:mem:aggexecnodetest;DB_CLOSE_DELAY=-1";
    final String DB_USER = "";
    final String DB_PASSWORD = "";
    conn = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);

    List<List<Object>> contents = new ArrayList<>();
    contents.add(Arrays.<Object>asList(1, "Anju", 15));
    contents.add(Arrays.<Object>asList(2, "Sonia", 17));
    contents.add(Arrays.<Object>asList(3, "Asha", 23));
    contents.add(Arrays.<Object>asList(4, "Joe", 14));
    contents.add(Arrays.<Object>asList(5, "JoJo", 18));
    contents.add(Arrays.<Object>asList(6, "Sam", 18));
    contents.add(Arrays.<Object>asList(7, "Alice", 18));
    contents.add(Arrays.<Object>asList(8, "Bob", 18));
    stmt = conn.createStatement();
    stmt.execute("CREATE SCHEMA IF NOT EXISTS DEFAULT");
    stmt.execute("DROP TABLE DEFAULT.PEOPLE IF EXISTS");
    stmt.execute("CREATE TABLE DEFAULT.PEOPLE(id smallint, name varchar(255), age int)");
    for (List<Object> row : contents) {
      String id = row.get(0).toString();
      String name = row.get(1).toString();
      String age = row.get(2).toString();
      stmt.execute(String.format("INSERT INTO DEFAULT.PEOPLE(id, name, age) VALUES(%s, '%s', %s)", id, name, age));
    }
    
    // create a scrambled table
    int aggBlockCount = 2;
    UniformScrambler scrambler =
        new UniformScrambler("default", "people", "default", "scrambled_people", aggBlockCount);
    ScrambleMetaForTable tablemeta = scrambler.generateMeta();
    scrambledTable = tablemeta.getTableName();
    meta.insertScrambleMetaEntry(tablemeta);
    CreateTableAsSelect createQuery = scrambler.scrambledTableCreationQuery();
    CreateTableToSql createToSql = new CreateTableToSql(new H2Syntax());
    String scrambleSql = createToSql.toSql(createQuery);
    conn.createStatement().execute(String.format("DROP TABLE IF EXISTS %s.%s", "default", "scrambled_people"));
    conn.createStatement().execute(scrambleSql);
  }

  @Test
  public void testSingleExecute() throws VerdictDbException {
    BaseTable base = new BaseTable("default", scrambledTable, "t");
    String aliasName = "a";
    SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
        Arrays.<SelectItem>asList(
            new AliasedColumn(new ColumnOp("sum", new BaseColumn("t", "age")), aliasName)),
        base);
    DbmsConnection dbmsConn = new JdbcConnection(conn, new HiveSyntax());
    AggExecutionNode node = new AggExecutionNode(dbmsConn, meta, relation);
    DbmsQueryResult rs = node.singleExecute();
    rs.printContent();
  }

}
