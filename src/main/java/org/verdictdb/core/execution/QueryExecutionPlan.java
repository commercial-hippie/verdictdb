/*
 * Copyright 2018 University of Michigan
 * 
 * You must contact Barzan Mozafari (mozafari@umich.edu) or Yongjoo Park (pyongjoo@umich.edu) to discuss
 * how you could use, modify, or distribute this code. By default, this code is not open-sourced and we do
 * not license this code.
 */

package org.verdictdb.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.core.execution.ola.AggExecutionNodeBlock;
import org.verdictdb.core.query.BaseTable;
import org.verdictdb.core.query.SelectQuery;
import org.verdictdb.core.query.SubqueryColumn;
import org.verdictdb.core.query.UnnamedColumn;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.exception.VerdictDBTypeException;
import org.verdictdb.exception.VerdictDBValueException;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.resulthandler.StandardOutputHandler;
import org.verdictdb.sql.syntax.SyntaxAbstract;

public class QueryExecutionPlan {
  
//  SelectQuery query;
  
  ScrambleMeta scrambleMeta;
  
  QueryExecutionNode root;
  
  String scratchpadSchemaName;
  
//  PostProcessor postProcessor;
  
//  /**
//   * 
//   * @param queryString A select query
//   * @throws UnexpectedTypeException 
//   */
//  public AggQueryExecutionPlan(DbmsConnection conn, SyntaxAbstract syntax, String queryString) throws VerdictDbException {
//    this(conn, syntax, (SelectQueryOp) new NonValidatingSQLParser().toRelation(queryString));
//  }
  
  final int serialNum = ThreadLocalRandom.current().nextInt(0, 1000000);
  
  int identifierNum = 0;

  int tempTableNameNum = 0;
  
  public QueryExecutionPlan(String scratchpadSchemaName) {
    this.scratchpadSchemaName = scratchpadSchemaName;
    this.scrambleMeta = new ScrambleMeta();
  }
  
  public QueryExecutionPlan(String scratchpadSchemaName, ScrambleMeta scrambleMeta) {
    this.scratchpadSchemaName = scratchpadSchemaName;
    this.scrambleMeta = scrambleMeta;
  }

  /**
   * 
   * @param query  A well-formed select query object
   * @throws VerdictDBValueException 
   * @throws VerdictDBException 
   */
  public QueryExecutionPlan(
      String scratchpadSchemaName,
      ScrambleMeta scrambleMeta,
      SelectQuery query) throws VerdictDBException {
    this(scratchpadSchemaName);
    setScrambleMeta(scrambleMeta);
    setSelectQuery(query);
  }
  
  public int getSerialNumber() {
    return serialNum;
  }
  
  public ScrambleMeta getScrambleMeta() {
    return scrambleMeta;
  }
  
  public void setScrambleMeta(ScrambleMeta scrambleMeta) {
    this.scrambleMeta = scrambleMeta;
  }
  
  public void setSelectQuery(SelectQuery query) throws VerdictDBException {
    if (!query.isAggregateQuery()) {
      throw new VerdictDBTypeException(query);
    }
    this.root = makePlan(query);
  }
  
  public String getScratchpadSchemaName() {
    return scratchpadSchemaName;
  }
  
  String generateUniqueIdentifier() {
    return String.format("%d_%d", serialNum, identifierNum++);
  }

  public String generateAliasName() {
    return String.format("verdictdbalias_%s", generateUniqueIdentifier());
  }

  public Pair<String, String> generateTempTableName() {
  //    return Pair.of(scratchpadSchemaName, String.format("verdictdbtemptable_%d", tempTableNameNum++));
      return Pair.of(scratchpadSchemaName, String.format("verdictdbtemptable_%s", generateUniqueIdentifier()));
    }

  /** 
   * Creates a tree in which each node is QueryExecutionNode. Each AggQueryExecutionNode corresponds to
   * an aggregate query, whether it is the main query or a subquery.
   * 
   * 1. Each QueryExecutionNode is supposed to run on a separate thread.
   * 2. Restrict the aggregate subqueries to appear in the where clause or in the from clause 
   *    (i.e., not in the select list, not in having or group-by)
   * 3. Each node cannot include any correlated predicate (i.e., the column that appears in outer queries).
   *   (1) In the future, we should convert a correlated subquery into a joined subquery (if possible).
   *   (2) Otherwise, the entire query including a correlated subquery must be the query of a single node.
   * 4. The results of AggNode and ProjectionNode are stored as a materialized view; the names of those
   *    materialized views are passed to their parents for potential additional processing or reporting.
   * 
   * //@param conn
   * @param query
   * @return Pair of roots of the tree and post-processing interface.
   * @throws VerdictDBValueException 
   * @throws VerdictDBTypeException 
   */
  QueryExecutionNode makePlan(SelectQuery query) throws VerdictDBException {
    QueryExecutionNode root = SelectAllExecutionNode.create(this, query);
//    root = makeAsyncronousAggIfAvailable(root);
    return root;
  }

  /**
   *
   * @param root The root execution node of ALL nodes (i.e., not just the top agg node)
   * @return
   * @throws VerdictDBException
   */
  QueryExecutionNode makeAsyncronousAggIfAvailable(QueryExecutionNode root) throws VerdictDBException {
    List<AggExecutionNodeBlock> aggBlocks = root.identifyTopAggBlocks();

//    List<QueryExecutionNode> newNodes = new ArrayList<>();
//    for (QueryExecutionNode node : topAggNodes) {
//      QueryExecutionNode newNode = null;
//      if (((AggExecutionNode) node).doesContainScrambledTablesInDescendants(scrambleMeta)) {
//        newNode = ((AggExecutionNode) node).toAsyncAgg(scrambleMeta);
//      } else {
//        newNode = node;
//      }
//      newNodes.add(newNode);
//    }

    // converted nodes should be used in place of the original nodes.
    for (int i = 0; i < aggBlocks.size(); i++) {
      AggExecutionNodeBlock nodeBlock = aggBlocks.get(i);
      QueryExecutionNode oldNode = nodeBlock.getBlockRootNode();
      QueryExecutionNode newNode = nodeBlock.convertToProgressiveAgg();

      List<QueryExecutionNode> parents = oldNode.getParents();
      for (QueryExecutionNode parent : parents) {
        List<QueryExecutionNode> parentDependants = parent.getDependents();
        int idx = parentDependants.indexOf(oldNode);
        parentDependants.remove(idx);
        parentDependants.add(idx, newNode);
      }
    }

    return root;
  }
  
  public void execute(DbmsConnection conn, ExecutionTokenQueue queue) {
    // execute roots

    // after executions are all finished.
    cleanUp();
  }
  
  // clean up any intermediate materialized tables
  void cleanUp() {
    tempTableNameNum = 0;
  }
//  static void resetTempTableNameNum() {tempTableNameNum = 0;}

  public void compress() {
    List<QueryExecutionNode> nodesToCompress = new ArrayList<>();
    // compress the node from bottom to up in order to replace the select query conveniently
    List<QueryExecutionNode> traverse = new ArrayList<>();
    traverse.add(root);
    while (!traverse.isEmpty()) {
      QueryExecutionNode node = traverse.get(0);
      traverse.remove(0);
      if (node.dependents.isEmpty()) {
        nodesToCompress.add(node);
      }
      else traverse.addAll(node.dependents);
    }

    while (!nodesToCompress.isEmpty()) {
      QueryExecutionNode node = nodesToCompress.get(0);
      nodesToCompress.remove(0);
      // Exception 1: has no parent(root), or has multiple parent
      // Exception 2: has multiple dependents and dependents share same queue
      boolean compressable = node.parents.size()==1 && !(node.dependents.size()>1 &&
          node.dependents.get(0).broadcastingQueues.equals(node.dependents.get(1).broadcastingQueues));
      if (compressable) {
        QueryExecutionNode parent = node.parents.get(0);
        compressTwoNode(node, parent);
      }
      nodesToCompress.addAll(node.parents);
    }
  }

  // Compress node and parent into parent, node will be useless
  void compressTwoNode(QueryExecutionNode node, QueryExecutionNode parent) {

    // Change the query of parents
    BaseTable placeholderTableinParent = ((QueryExecutionNodeWithPlaceHolders)parent).getPlaceholderTables().get(parent.dependents.indexOf(node));

    // If temp table is in from list of parent, just direct replace with the select query of node
    if (parent.selectQuery.getFromList().contains(placeholderTableinParent)) {
      int index = parent.selectQuery.getFromList().indexOf(placeholderTableinParent);
      node.selectQuery.setAliasName(parent.selectQuery.getFromList().get(index).getAliasName().get());
      parent.selectQuery.getFromList().set(index, node.selectQuery);
    }
    // Otherwise, it need to search filter to find the temp table
    else {
      List<SubqueryColumn> placeholderTablesinFilter = ((QueryExecutionNodeWithPlaceHolders)parent).getPlaceholderTablesinFilter();
      for (SubqueryColumn filter:placeholderTablesinFilter) {
        if (filter.getSubquery().getFromList().size()==1 && filter.getSubquery().getFromList().get(0).equals(placeholderTableinParent)) {
          filter.setSubquery(node.selectQuery);
        }
      }
    }

    // Compress the node tree
    parent.listeningQueues.removeAll(node.broadcastingQueues);
    parent.listeningQueues.addAll(node.listeningQueues);
    parent.dependents.remove(node);
    parent.dependents.addAll(node.dependents);
    for (QueryExecutionNode dependent:node.dependents) {
      dependent.parents.remove(node);
      dependent.parents.add(parent);
    }
  }
}
