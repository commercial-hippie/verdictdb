package org.verdictdb.connection;

import org.verdictdb.exception.VerdictDBDbmsException;
import org.verdictdb.sqlsyntax.SqlSyntax;

public interface DbmsConnection extends MetaDataProvider {
  
  /**
   * Executes a query (or queries). If the result exists, return it.
   * 
   * If a query includes multiple queries separated by semicolons, issue them separately in order. 
   * 
   * @param query
   * @return
   * @throws VerdictDBDbmsException
   */
  public DbmsQueryResult execute(String query) throws VerdictDBDbmsException;
  
//  /**
//   * 
//   * @param sql
//   * @return either (1) the row count for SQL Data Manipulation Language (DML) statements or (2) 0 for 
//   * SQL statements that return nothing
//   */
//  public int executeUpdate(String query) throws VerdictDBDbmsException;
  
  public SqlSyntax getSyntax();

//  public Connection getConnection();
  
  public void close();

}