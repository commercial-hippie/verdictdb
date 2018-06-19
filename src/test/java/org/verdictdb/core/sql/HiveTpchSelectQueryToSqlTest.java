package org.verdictdb.core.sql;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.klarna.hiverunner.HiveShell;
import com.klarna.hiverunner.StandaloneHiveRunner;
import com.klarna.hiverunner.annotations.HiveSQL;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.verdictdb.core.query.*;
import org.verdictdb.exception.VerdictDbException;
import org.verdictdb.sql.syntax.HiveSyntax;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(StandaloneHiveRunner.class)
public class HiveTpchSelectQueryToSqlTest {

    @HiveSQL(files = {})

    private HiveShell shell;

    @Before
    public void setupSourceDatabase() throws Exception {
        shell.execute("CREATE DATABASE tpch");
        File schemaFile = new File("src/test/resources/tpch-schema.sql");
        String schemas = Files.toString(schemaFile, Charsets.UTF_8);
        for (String schema : schemas.split(";")) {
            schema += ";"; // add semicolon at the end
            schema = schema.trim();
            shell.execute(schema);
        }
    }

    @Test
    public void Query1Test() throws VerdictDbException {
        BaseTable base = new BaseTable("tpch", "lineitem", "t");
        List<UnnamedColumn> operand1 = Arrays.<UnnamedColumn>asList(
                ConstantColumn.valueOf(1),
                new BaseColumn("t", "l_discount"));
        List<UnnamedColumn> operand2 = Arrays.<UnnamedColumn>asList(
                new BaseColumn("t", "l_extendedprice"),
                new ColumnOp("subtract", operand1));
        List<UnnamedColumn> operand3 = Arrays.<UnnamedColumn>asList(
                ConstantColumn.valueOf(1),
                new BaseColumn("t", "l_tax"));
        List<UnnamedColumn> operand4 = Arrays.<UnnamedColumn>asList(
                new ColumnOp("multiply", operand2),
                new ColumnOp("add", operand3));
        List<UnnamedColumn> operand5 = Arrays.<UnnamedColumn>asList(
                new BaseColumn("t", "l_shipdate"),
                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'1998-09-16'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'5'"), ConstantColumn.valueOf("day")))
                )));
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("t", "l_returnflag"),
                        new BaseColumn("t", "l_linestatus"),
                        new AliasedColumn(new ColumnOp("sum", new BaseColumn("t", "l_quantity")), "sum_qty"),
                        new AliasedColumn(new ColumnOp("sum", new BaseColumn("t", "l_extendedprice")), "sum_base_price"),
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("multiply", operand2)), "sum_disc_price"),
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("multiply", operand4)), "sum_charge"),
                        new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "l_quantity")), "avg_qty"),
                        new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "l_extendedprice")), "avg_price"),
                        new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "l_discount")), "avg_disc"),
                        new AliasedColumn(new ColumnOp("count", new AsteriskColumn()), "count_order")
                ),
                base, new ColumnOp("lessequal", operand5));
        relation.addGroupby(Arrays.<GroupingAttribute>asList(new AliasReference("l_returnflag"),
                new AliasReference("l_linestatus")));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(new OrderbyAttribute("l_returnflag"),
                new OrderbyAttribute("l_linestatus")));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query2Test() throws VerdictDbException {
        BaseTable part = new BaseTable("tpch", "part", "p");
        BaseTable supplier = new BaseTable("tpch", "supplier", "s");
        BaseTable partsupp = new BaseTable("tpch", "partsupp", "ps");
        BaseTable nation = new BaseTable("tpch", "nation", "n");
        BaseTable region = new BaseTable("tpch", "region", "r");
        List<AbstractRelation> from = Arrays.<AbstractRelation>asList(part, supplier, partsupp, nation, region);
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("s", "s_acctbal"),
                        new BaseColumn("s", "s_name"),
                        new BaseColumn("n", "n_name"),
                        new BaseColumn("p", "p_partkey"),
                        new BaseColumn("p", "p_mfgr"),
                        new BaseColumn("s", "s_address"),
                        new BaseColumn("s", "s_phone"),
                        new BaseColumn("s", "s_comment")),
                from);
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_partkey"),
                new BaseColumn("ps", "ps_partkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                new BaseColumn("ps", "ps_suppkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_size"),
                ConstantColumn.valueOf("'1'")
        )));
        relation.addFilterByAnd(new ColumnOp("like", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_type"),
                ConstantColumn.valueOf("'%ab'")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_regionkey"),
                new BaseColumn("r", "r_regionkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("r", "r_name"),
                ConstantColumn.valueOf("'abc'")
        )));
        List<AbstractRelation> subqueryFrom = Arrays.<AbstractRelation>asList(partsupp, supplier, nation, region);
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new ColumnOp("min", new BaseColumn("ps", "ps_supplycost"))),
                subqueryFrom);
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_partkey"),
                new BaseColumn("ps", "ps_partkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                new BaseColumn("ps", "ps_suppkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_regionkey"),
                new BaseColumn("r", "r_regionkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("r", "r_name"),
                ConstantColumn.valueOf("'abc'")
        )));
        //relation.addFilterByAnd(new ColumnOp("equal", Arrays.asList(
        //        new BaseColumn("ps", "ps_supplycost"),
        //        SubqueryColumn.getSubqueryColumn(subquery)
        //)));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(
                new OrderbyAttribute("s_acctbal", "desc"),
                new OrderbyAttribute("n_name"),
                new OrderbyAttribute("s_name"),
                new OrderbyAttribute("p_partkey")
        ));
        relation.addLimit(ConstantColumn.valueOf(100));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(0, result.size());
    }

    @Test
    public void Query3Test() throws VerdictDbException {
        AbstractRelation customer = new BaseTable("tpch", "customer", "c");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        ColumnOp op1 = new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_extendedprice"),
                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                        ConstantColumn.valueOf(1),
                        new BaseColumn("l", "l_discount")
                ))
        ));
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("l", "l_orderkey"),
                        new AliasedColumn(new ColumnOp("sum", op1), "revenue"),
                        new BaseColumn("o", "o_orderdate"),
                        new BaseColumn("o", "o_shippriority")
                ),
                Arrays.asList(customer, orders, lineitem));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_mktsegment"),
                ConstantColumn.valueOf("':1'")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_custkey"),
                new BaseColumn("o", "o_custkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_orderkey"),
                new BaseColumn("o", "o_orderkey")
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'2006-01-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'2006-01-01'"))
        )));
        relation.addGroupby(Arrays.<GroupingAttribute>asList(
                new AliasReference("l_orderkey"),
                new AliasReference("o_orderdate"),
                new AliasReference("o_shippriority")
        ));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(
                new OrderbyAttribute("revenue", "desc"),
                new OrderbyAttribute("o_orderdate")
        ));
        relation.addLimit(ConstantColumn.valueOf(10));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(0, result.size());
    }

    @Test
    public void Query4Test() throws VerdictDbException {
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("o", "o_orderpriority"),
                        new AliasedColumn(new ColumnOp("count", new AsteriskColumn()), "order_count")
                ),
                orders);
        relation.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'2006-01-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'2006-01-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'1'"), ConstantColumn.valueOf("month")))
                ))
        )));
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new AsteriskColumn()),
                new BaseTable("tpch", "lineitem", "l"));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_orderkey"),
                new BaseColumn("o", "o_orderkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_commitdate"),
                new BaseColumn("l", "l_receiptdate")
        )));
        relation.addFilterByAnd(new ColumnOp("exists", SubqueryColumn.getSubqueryColumn(subquery)));
        relation.addGroupby(new AliasReference("o_orderpriority"));
        relation.addOrderby(new OrderbyAttribute("o_orderpriority"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(0, result.size());
    }

    @Test
    public void Query5Test() throws VerdictDbException {
        AbstractRelation customer = new BaseTable("tpch", "customer", "c");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation nation = new BaseTable("tpch", "nation", "n");
        AbstractRelation region = new BaseTable("tpch", "region", "r");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("n", "n_name"),
                        new AliasedColumn(new ColumnOp("sum", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("l", "l_extendedprice"),
                                        new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                                                ConstantColumn.valueOf(1),
                                                new BaseColumn("l", "l_discount")
                                        ))
                                ))
                        )), "revenue")
                ),
                Arrays.asList(customer, orders, lineitem, supplier, nation, region));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_custkey"),
                new BaseColumn("o", "o_custkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_orderkey"),
                new BaseColumn("o", "o_orderkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_suppkey"),
                new BaseColumn("s", "s_suppkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_nationkey"),
                new BaseColumn("s", "s_nationkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_regionkey"),
                new BaseColumn("r", "r_regionkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("r", "r_name"),
                ConstantColumn.valueOf("':1'")
        )));
        relation.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'2016-01-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'2016-01-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'1'"), ConstantColumn.valueOf("year")))
                ))
        )));
        relation.addGroupby(new AliasReference("n_name"));
        relation.addOrderby(new OrderbyAttribute("revenue", "desc"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(0, result.size());
    }

    @Test
    public void Query6Test() throws VerdictDbException {
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("multiply",
                                Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("l", "l_extendedprice"),
                                        new BaseColumn("l", "l_discount")
                                ))), "revenue")
                ),
                lineitem);
        relation.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1993-01-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'1994-01-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(
                                ConstantColumn.valueOf("'1'"),
                                ConstantColumn.valueOf("year")
                        ))
                ))
        )));
        relation.addFilterByAnd(new ColumnOp("between", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_discount"),
                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("0.06"), ConstantColumn.valueOf("0.01"))),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("0.06"), ConstantColumn.valueOf("0.01")))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_quantity"),
                ConstantColumn.valueOf("25"))
        ));
        relation.addLimit(ConstantColumn.valueOf(1));
        String expected = "select " +
                "sum(`l`.`l_extendedprice` * `l`.`l_discount`) as revenue " +
                "from " +
                "`tpch`.`lineitem` as l " +
                "where " +
                "(((`l`.`l_shipdate` >= (date ':1')) " +
                "and (`l`.`l_shipdate` < ((date ':1') + (interval '1' year)))) " +
                "and (`l`.`l_discount` between (':2' - 0.01) and (':2' + 0.01))) " +
                "and (`l`.`l_quantity` < ':3') " +
                "limit 1";
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query7Test() throws VerdictDbException {
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation customer = new BaseTable("tpch", "customer", "c");
        AbstractRelation nation1 = new BaseTable("tpch", "nation", "n1");
        AbstractRelation nation2 = new BaseTable("tpch", "nation", "n2");
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new BaseColumn("n1", "n_name"), "supp_nation"),
                        new AliasedColumn(new BaseColumn("n2", "n_name"), "cust_nation"),
                        new AliasedColumn(new ColumnOp("substr", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_shipdate"), ConstantColumn.valueOf(0), ConstantColumn.valueOf(4))), "l_year"),
                        new AliasedColumn(new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_extendedprice"),
                                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                                        ConstantColumn.valueOf(1), new BaseColumn("l", "l_discount")))
                        )), "volume")
                ),
                Arrays.asList(supplier, lineitem, orders, customer, nation1, nation2));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                new BaseColumn("l", "l_suppkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderkey"),
                new BaseColumn("l", "l_orderkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_custkey"),
                new BaseColumn("o", "o_custkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n1", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_nationkey"),
                new BaseColumn("n2", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("or", Arrays.<UnnamedColumn>asList(
                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("n1", "n_name"),
                                ConstantColumn.valueOf("'KENYA'")
                        )),
                        new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("n2", "n_name"),
                                ConstantColumn.valueOf("'PERU'")
                        ))
                )),
                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("n1", "n_name"),
                                ConstantColumn.valueOf("'PERU'")
                        )),
                        new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("n2", "n_name"),
                                ConstantColumn.valueOf("'KENYA'")
                        ))
                ))
        )));
        subquery.addFilterByAnd(new ColumnOp("between", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1995-01-01'")),
                new ColumnOp("date", ConstantColumn.valueOf("'1996-12-31'")))
        ));
        subquery.setAliasName("shipping");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("shipping", "supp_nation"),
                        new BaseColumn("shipping", "cust_nation"),
                        new BaseColumn("shipping", "l_year"),
                        new AliasedColumn(new ColumnOp("sum", new BaseColumn("shipping", "volume")), "revenue")
                ),
                subquery);
        relation.addGroupby(Arrays.<GroupingAttribute>asList(
                new AliasReference("supp_nation"),
                new AliasReference("cust_nation"),
                new AliasReference("l_year")
        ));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(
                new OrderbyAttribute("supp_nation"),
                new OrderbyAttribute("cust_nation"),
                new OrderbyAttribute("l_year")
        ));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query8Test() throws VerdictDbException {
        AbstractRelation part = new BaseTable("tpch", "part", "p");
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation customer = new BaseTable("tpch", "customer", "c");
        AbstractRelation nation1 = new BaseTable("tpch", "nation", "n1");
        AbstractRelation nation2 = new BaseTable("tpch", "nation", "n2");
        AbstractRelation region = new BaseTable("tpch", "region", "r");
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new ColumnOp("substr", Arrays.<UnnamedColumn>asList(new BaseColumn("o", "o_orderdate"), ConstantColumn.valueOf(0), ConstantColumn.valueOf(4))), "o_year"),
                        new AliasedColumn(new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_extendedprice"),
                                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf(1), new BaseColumn("l", "l_discount")))
                        )), "volume"),
                        new AliasedColumn(new BaseColumn("n2", "n_name"), "nation")
                ),
                Arrays.asList(part, supplier, lineitem, orders, customer, nation1, nation2, region));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_partkey"),
                new BaseColumn("l", "l_partkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                new BaseColumn("l", "l_suppkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_orderkey"),
                new BaseColumn("o", "o_orderkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_custkey"),
                new BaseColumn("c", "c_custkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_nationkey"),
                new BaseColumn("n1", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n1", "n_regionkey"),
                new BaseColumn("r", "r_regionkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("r", "r_name"),
                ConstantColumn.valueOf("'AMERICA'")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n2", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("between", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1995-01-01'")),
                new ColumnOp("date", ConstantColumn.valueOf("'1996-12-31'"))
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_type"),
                ConstantColumn.valueOf("'ECONOMY BURNISHED NICKEL'")
        )));
        subquery.setAliasName("all_nations");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("all_nations", "o_year"),
                        new AliasedColumn(
                                new ColumnOp("divide", Arrays.<UnnamedColumn>asList(
                                        new ColumnOp("sum", new ColumnOp("whenthenelse", Arrays.<UnnamedColumn>asList(
                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                        new BaseColumn("all_nations", "nation"),
                                                        ConstantColumn.valueOf("'PERU'")
                                                )), new BaseColumn("all_nations", "volume"),
                                                ConstantColumn.valueOf(0)))),
                                        new ColumnOp("sum", new BaseColumn("all_nations", "volume")))), "mkt_share"

                        )),
                subquery);
        relation.addGroupby(new AliasReference("o_year"));
        relation.addOrderby(new OrderbyAttribute("o_year"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query9Test() throws VerdictDbException {
        AbstractRelation part = new BaseTable("tpch", "part", "p");
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation partsupp = new BaseTable("tpch", "partsupp", "ps");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation nation = new BaseTable("tpch", "nation", "n");
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new BaseColumn("n", "n_name"), "nation"),
                        new AliasedColumn(new ColumnOp("substr", Arrays.<UnnamedColumn>asList(new BaseColumn("o", "o_orderdate"), ConstantColumn.valueOf(0), ConstantColumn.valueOf(4))), "o_year"),
                        new AliasedColumn(new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("l", "l_extendedprice"),
                                        new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf(1), new BaseColumn("l", "l_discount")))
                                )),
                                new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("ps", "ps_supplycost"),
                                        new BaseColumn("l", "l_quantity")
                                ))
                        )), "amount")
                ),
                Arrays.asList(part, supplier, lineitem, partsupp, orders, nation));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                new BaseColumn("l", "l_suppkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("ps", "ps_suppkey"),
                new BaseColumn("l", "l_suppkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("ps", "ps_partkey"),
                new BaseColumn("l", "l_partkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_partkey"),
                new BaseColumn("l", "l_partkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderkey"),
                new BaseColumn("l", "l_orderkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("like", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_name"),
                ConstantColumn.valueOf("'%plum%'")
        )));
        subquery.setAliasName("profit");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("profit", "nation"),
                        new BaseColumn("profit", "o_year"),
                        new AliasedColumn(new ColumnOp("sum", new BaseColumn("profit", "amount")), "sum_profit")
                ),
                subquery);
        relation.addGroupby(Arrays.<GroupingAttribute>asList(new AliasReference("nation"), new AliasReference("o_year")));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(new OrderbyAttribute("nation"),
                new OrderbyAttribute("o_year", "desc")));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query10Test() throws VerdictDbException {
        AbstractRelation customer = new BaseTable("tpch", "customer", "c");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation nation = new BaseTable("tpch", "nation", "n");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("c", "c_custkey"),
                        new BaseColumn("c", "c_name"),
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_extendedprice"),
                                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                                        ConstantColumn.valueOf(1),
                                        new BaseColumn("l", "l_discount")
                                ))
                        ))), "revenue"),
                        new BaseColumn("c", "c_acctbal"),
                        new BaseColumn("n", "n_name"),
                        new BaseColumn("c", "c_address"),
                        new BaseColumn("c", "c_phone"),
                        new BaseColumn("c", "c_comment")
                ),
                Arrays.asList(customer, orders, lineitem, nation));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_custkey"),
                new BaseColumn("o", "o_custkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_orderkey"),
                new BaseColumn("o", "o_orderkey")
        )));
        relation.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1993-07-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'1993-10-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'3'"), ConstantColumn.valueOf("month")))
                )
                ))));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_returnflag"),
                ConstantColumn.valueOf("'R'")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        relation.addGroupby(Arrays.<GroupingAttribute>asList(
                new AliasReference("c_custkey"),
                new AliasReference("c_name"),
                new AliasReference("c_acctbal"),
                new AliasReference("c_phone"),
                new AliasReference("n_name"),
                new AliasReference("c_address"),
                new AliasReference("c_comment")
        ));
        relation.addOrderby(new OrderbyAttribute("revenue", "desc"));
        relation.addLimit(ConstantColumn.valueOf(20));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(20, result.size());
    }

    @Test
    public void Query11Test() throws VerdictDbException {
        AbstractRelation partsupp = new BaseTable("tpch", "partsupp", "ps");
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation nation = new BaseTable("tpch", "nation", "n");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("ps", "ps_partkey"),
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("ps", "ps_supplycost"),
                                new BaseColumn("ps", "ps_availqty")
                        ))), "value")
                ),
                Arrays.asList(partsupp, supplier, nation));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("ps", "ps_suppkey"),
                new BaseColumn("s", "s_suppkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_name"),
                ConstantColumn.valueOf("'GERMANY'")
        )));
        relation.addGroupby(new AliasReference("ps_partkey"));
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("sum", new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("ps", "ps_supplycost"),
                                        new BaseColumn("ps", "ps_availqty")
                                ))),
                                ConstantColumn.valueOf("0.0001")
                        ))
                ), Arrays.asList(partsupp, supplier, nation));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("ps", "ps_suppkey"),
                new BaseColumn("s", "s_suppkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_name"),
                ConstantColumn.valueOf("'Germany'")
        )));
        /*
        relation.addHavingByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new ColumnOp("sum", new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                        new BaseColumn("ps", "ps_supplycost"),
                        new BaseColumn("ps", "ps_availqty")
                ))),
                SubqueryColumn.getSubqueryColumn(subquery)
        )));
        */
        relation.addOrderby(new OrderbyAttribute("value", "desc"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query12Test() throws VerdictDbException {
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("l", "l_shipmode"),
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("whenthenelse", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("or", Arrays.<UnnamedColumn>asList(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("o", "o_orderpriority"),
                                        ConstantColumn.valueOf("'1-URGENT'")
                                        )),
                                        new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                new BaseColumn("o", "o_orderpriority"),
                                                ConstantColumn.valueOf("'2-HIGH'")
                                        ))
                                )),
                                ConstantColumn.valueOf(1),
                                ConstantColumn.valueOf(0)
                        ))), "high_line_count"),
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("whenthenelse", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(new ColumnOp("notequal", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("o", "o_orderpriority"),
                                        ConstantColumn.valueOf("'1-URGENT'")
                                        )),
                                        new ColumnOp("notequal", Arrays.<UnnamedColumn>asList(
                                                new BaseColumn("o", "o_orderpriority"),
                                                ConstantColumn.valueOf("'2-HIGH'")
                                        ))
                                )),
                                ConstantColumn.valueOf(1),
                                ConstantColumn.valueOf(0)
                        ))), "low_line_count")
                ),
                Arrays.asList(orders, lineitem));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderkey"),
                new BaseColumn("l", "l_orderkey")
        )));
        relation.addFilterByAnd(new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipmode"),
                ConstantColumn.valueOf("'REG AIR'"),
                ConstantColumn.valueOf("'MAIL'")
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_commitdate"),
                new BaseColumn("l", "l_receiptdate")
        )));

        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new BaseColumn("l", "l_commitdate")
        )));
        relation.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_receiptdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1995-01-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_receiptdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'1996-01-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'1'"), ConstantColumn.valueOf("year")))
                ))
        )));
        relation.addGroupby(new AliasReference("l_shipmode"));
        relation.addOrderby(new OrderbyAttribute("l_shipmode"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query13Test() throws VerdictDbException {
        BaseTable customer = new BaseTable("tpch", "customer", "c");
        BaseTable orders = new BaseTable("tpch", "orders", "o");
        JoinTable join = JoinTable.getJoinTable(Arrays.<AbstractRelation>asList(customer, orders),
                Arrays.<JoinTable.JoinType>asList(JoinTable.JoinType.leftouter),
                Arrays.<UnnamedColumn>asList(new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("c", "c_custkey"),
                                new BaseColumn("o", "o_custkey")
                        )),
                        new ColumnOp("notlike", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("o", "o_comment"),
                                ConstantColumn.valueOf("'%unusual%accounts%'")
                        ))
                ))));
        SelectQueryOp subqery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("c", "c_custkey"),
                        new AliasedColumn(new ColumnOp("count", new BaseColumn("o", "o_orderkey")),"c_count")
                ),
                join);
        subqery.addGroupby(new AliasReference("c_custkey"));
        subqery.setAliasName("c_orders");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("c_orders", "c_count"),
                        new AliasedColumn(new ColumnOp("count", new AsteriskColumn()), "custdist")
                ),
                subqery);
        relation.addGroupby(new AliasReference("c_count"));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(
                new OrderbyAttribute("custdist", "desc"),
                new OrderbyAttribute("c_count", "desc")));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query14Test() throws VerdictDbException {
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation part = new BaseTable("tpch", "part", "p");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new ColumnOp("divide", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                        ConstantColumn.valueOf("100.00"),
                                        new ColumnOp("sum", new ColumnOp("whenthenelse", Arrays.<UnnamedColumn>asList(
                                                new ColumnOp("like", Arrays.<UnnamedColumn>asList(
                                                        new BaseColumn("p", "p_type"),
                                                        ConstantColumn.valueOf("'PROMO%'")
                                                )),
                                                new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                                        new BaseColumn("l", "l_extendedprice"),
                                                        new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf(1), new BaseColumn("l", "l_discount"))))),
                                                ConstantColumn.valueOf(0)
                                        )))
                                )),
                                new ColumnOp("sum", new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("l", "l_extendedprice"),
                                        new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf(1), new BaseColumn("l", "l_discount")))
                                )))
                        )), "promo_revenue")
                ),
                Arrays.asList(lineitem, part));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_partkey"),
                new BaseColumn("p", "p_partkey")
        )));
        relation.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1995-07-01'"))
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'1995-08-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'1'"), ConstantColumn.valueOf("month")))
                ))
        )));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query15Test() throws VerdictDbException {
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        //AbstractRelation revenue = new BaseTable("tpch", "revenue", "r");
        //SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
        //        Arrays.<SelectItem>asList(new ColumnOp("max", new BaseColumn("r", "total_revenue"))),
        //        revenue);
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("s", "s_suppkey"),
                        new BaseColumn("s", "s_name"),
                        new BaseColumn("s", "s_address"),
                        new BaseColumn("s", "s_phone")
                        //new BaseColumn("r", "total_revenue")
                ),
                Arrays.asList(supplier));
        //relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
        //        new BaseColumn("s", "s_suppkey"),
        //        new BaseColumn("r", "supplier_no")
        //)));
        //relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
        //        new BaseColumn("r", "total_revenue"),
        //        SubqueryColumn.getSubqueryColumn(subquery)
        //)));
        relation.addOrderby(new OrderbyAttribute("s_suppkey"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query16Test() throws VerdictDbException {
        AbstractRelation partsupp = new BaseTable("tpch", "partsupp", "ps");
        AbstractRelation part = new BaseTable("tpch", "part", "p");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("p", "p_brand"),
                        new BaseColumn("p", "p_type"),
                        new BaseColumn("p", "p_size"),
                        new AliasedColumn(new ColumnOp("countdistinct", new BaseColumn("ps", "ps_suppkey")), "supplier_cnt")
                ),
                Arrays.asList(partsupp, part));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_partkey"),
                new BaseColumn("ps", "ps_partkey")
        )));
        relation.addFilterByAnd(new ColumnOp("notequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_brand"),
                ConstantColumn.valueOf("'Brand#34'")
        )));
        relation.addFilterByAnd(new ColumnOp("notlike", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_type"),
                ConstantColumn.valueOf("'ECONOMY BRUSHED%'")
        )));
        relation.addFilterByAnd(new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_size"),
                ConstantColumn.valueOf("22"), ConstantColumn.valueOf("14"), ConstantColumn.valueOf("27"), ConstantColumn.valueOf("49"),
                ConstantColumn.valueOf("31"), ConstantColumn.valueOf("33"), ConstantColumn.valueOf("35"), ConstantColumn.valueOf("28")
        )));
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new BaseColumn("s", "s_suppkey")),
                Arrays.<AbstractRelation>asList(new BaseTable("tpch", "supplier", "s")));
        subquery.addFilterByAnd(new ColumnOp("like", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_comment"),
                ConstantColumn.valueOf("'%Customer%Complaints%'")
        )));
        relation.addFilterByAnd(new ColumnOp("notin", Arrays.asList(
                new BaseColumn("ps", "ps_suppkey"),
                SubqueryColumn.getSubqueryColumn(subquery)
        )));
        relation.addGroupby(Arrays.<GroupingAttribute>asList(
                new AliasReference("p_brand"),
                new AliasReference("p_type"),
                new AliasReference("p_size")
        ));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(
                new OrderbyAttribute("supplier_cnt", "desc"),
                new OrderbyAttribute("p_brand"),
                new OrderbyAttribute("p_type"),
                new OrderbyAttribute("p_size")
        ));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query17Test() throws VerdictDbException {
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation part = new BaseTable("tpch", "part", "p");
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new BaseColumn("l", "l_partkey"), "agg_partkey"),
                        new AliasedColumn(new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                ConstantColumn.valueOf("0.2"),
                                new ColumnOp("avg", new BaseColumn("l", "l_quantity"))
                        )), "avg_quantity")
                ),
                lineitem);
        subquery.addGroupby(new AliasReference("l_partkey"));
        subquery.setAliasName("part_agg");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new ColumnOp("divide", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("sum", new BaseColumn("l", "l_extendedprice")),
                                ConstantColumn.valueOf("7.0")
                        )), "avg_yearly")
                ),
                Arrays.asList(lineitem, part, subquery));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_partkey"),
                new BaseColumn("l", "l_partkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("part_agg", "agg_partkey"),
                new BaseColumn("l", "l_partkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_brand"),
                ConstantColumn.valueOf("'Brand#24'")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_container"),
                ConstantColumn.valueOf("'MED BAG'")
        )));
        relation.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_quantity"),
                new BaseColumn("part_agg", "avg_quantity")
        )));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query18Test() throws VerdictDbException {
        AbstractRelation customer = new BaseTable("tpch", "customer", "c");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("c", "c_name"),
                        new BaseColumn("c", "c_custkey"),
                        new BaseColumn("o", "o_orderkey"),
                        new BaseColumn("o", "o_orderdate"),
                        new BaseColumn("o", "o_totalprice"),
                        new ColumnOp("sum", new BaseColumn("l", "l_quantity"))
                ),
                Arrays.asList(customer, orders, lineitem));
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new BaseColumn("l", "l_orderkey")),
                lineitem);
        subquery.addGroupby(new AliasReference("l_orderkey"));
        subquery.addHavingByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new ColumnOp("sum", new BaseColumn("l", "l_quantity")),
                ConstantColumn.valueOf("300")
        )));
        relation.addFilterByAnd(new ColumnOp("in", Arrays.asList(
                new BaseColumn("o", "o_orderkey"),
                SubqueryColumn.getSubqueryColumn(subquery)
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_custkey"),
                new BaseColumn("o", "o_custkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderkey"),
                new BaseColumn("l", "l_orderkey")
        )));
        relation.addGroupby(Arrays.<GroupingAttribute>asList(
                new AliasReference("c_name"),
                new AliasReference("c_custkey"),
                new AliasReference("o_orderkey"),
                new AliasReference("o_orderdate"),
                new AliasReference("o_totalprice")
        ));
        relation.addOrderby(Arrays.<OrderbyAttribute>asList(
                new OrderbyAttribute("o_totalprice", "desc"),
                new OrderbyAttribute("o_orderdate")
        ));
        relation.addLimit(ConstantColumn.valueOf(100));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query19Test() throws VerdictDbException {
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l");
        AbstractRelation part = new BaseTable("tpch", "part", "p");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new ColumnOp("sum", new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_extendedprice"),
                                new ColumnOp("subtract", Arrays.<UnnamedColumn>asList(
                                        ConstantColumn.valueOf(1),
                                        new BaseColumn("l", "l_discount")
                                ))
                        ))), "revenue")
                ),
                Arrays.asList(lineitem, part));
        ColumnOp columnOp1 = new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                                        new BaseColumn("p", "p_partkey"),
                                                                        new BaseColumn("l", "l_partkey")
                                                                )),
                                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                                        new BaseColumn("p", "p_brand"),
                                                                        ConstantColumn.valueOf("'Brand#32'")
                                                                ))
                                                        )),
                                                        new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                                                                new BaseColumn("p", "p_container"),
                                                                ConstantColumn.valueOf("'SM CASE'"),
                                                                ConstantColumn.valueOf("'SM BOX'"),
                                                                ConstantColumn.valueOf("'SM PACK'"),
                                                                ConstantColumn.valueOf("'SM PKG'")
                                                        ))
                                                )),
                                                new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                                                        new BaseColumn("l", "l_quantity"),
                                                        ConstantColumn.valueOf("'7'")
                                                ))
                                        )),
                                        new ColumnOp("lessequal", Arrays.<UnnamedColumn>asList(
                                                new BaseColumn("l", "l_quantity"),
                                                new ColumnOp("add", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'7'"), ConstantColumn.valueOf(10)))
                                        ))
                                )),
                                new ColumnOp("between", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("p", "p_size"),
                                        ConstantColumn.valueOf(1),
                                        ConstantColumn.valueOf(5)
                                ))
                        )),
                        new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_shipmode"),
                                ConstantColumn.valueOf("'AIR'"),
                                ConstantColumn.valueOf("'AIR REG'")
                        ))
                )),
                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                        new BaseColumn("l", "l_shipinstruct"),
                        ConstantColumn.valueOf("'DELIVER IN PERSON'")
                ))
        ));
        ColumnOp columnOp2 = new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                                        new BaseColumn("p", "p_partkey"),
                                                                        new BaseColumn("l", "l_partkey")
                                                                )),
                                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                                        new BaseColumn("p", "p_brand"),
                                                                        ConstantColumn.valueOf("'Brand#35'")
                                                                ))
                                                        )),
                                                        new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                                                                new BaseColumn("p", "p_container"),
                                                                ConstantColumn.valueOf("'MED BAG'"),
                                                                ConstantColumn.valueOf("'MED BOX'"),
                                                                ConstantColumn.valueOf("'MED PKG'"),
                                                                ConstantColumn.valueOf("'MED PACK'")
                                                        ))
                                                )),
                                                new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                                                        new BaseColumn("l", "l_quantity"),
                                                        ConstantColumn.valueOf("'15'")
                                                ))
                                        )),
                                        new ColumnOp("lessequal", Arrays.<UnnamedColumn>asList(
                                                new BaseColumn("l", "l_quantity"),
                                                new ColumnOp("add", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'15'"), ConstantColumn.valueOf(10)))
                                        ))
                                )),
                                new ColumnOp("between", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("p", "p_size"),
                                        ConstantColumn.valueOf(1),
                                        ConstantColumn.valueOf(10)
                                ))
                        )),
                        new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_shipmode"),
                                ConstantColumn.valueOf("'AIR'"),
                                ConstantColumn.valueOf("'AIR REG'")
                        ))
                )),
                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                        new BaseColumn("l", "l_shipinstruct"),
                        ConstantColumn.valueOf("'DELIVER IN PERSON'")
                ))
        ));
        ColumnOp columnOp3 = new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                        new ColumnOp("and", Arrays.<UnnamedColumn>asList(
                                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                                        new BaseColumn("p", "p_partkey"),
                                                                        new BaseColumn("l", "l_partkey")
                                                                )),
                                                                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                                                                        new BaseColumn("p", "p_brand"),
                                                                        ConstantColumn.valueOf("'Brand#24'")
                                                                ))
                                                        )),
                                                        new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                                                                new BaseColumn("p", "p_container"),
                                                                ConstantColumn.valueOf("'LG CASE'"),
                                                                ConstantColumn.valueOf("'LG BOX'"),
                                                                ConstantColumn.valueOf("'LG PACK'"),
                                                                ConstantColumn.valueOf("'LG PKG'")
                                                        ))
                                                )),
                                                new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                                                        new BaseColumn("l", "l_quantity"),
                                                        ConstantColumn.valueOf("'26'")
                                                ))
                                        )),
                                        new ColumnOp("lessequal", Arrays.<UnnamedColumn>asList(
                                                new BaseColumn("l", "l_quantity"),
                                                new ColumnOp("add", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'26'"), ConstantColumn.valueOf(10)))
                                        ))
                                )),
                                new ColumnOp("between", Arrays.<UnnamedColumn>asList(
                                        new BaseColumn("p", "p_size"),
                                        ConstantColumn.valueOf(1),
                                        ConstantColumn.valueOf(15)
                                ))
                        )),
                        new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("l", "l_shipmode"),
                                ConstantColumn.valueOf("'AIR'"),
                                ConstantColumn.valueOf("'AIR REG'")
                        ))
                )),
                new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                        new BaseColumn("l", "l_shipinstruct"),
                        ConstantColumn.valueOf("'DELIVER IN PERSON'")
                ))
        ));
        relation.addFilterByAnd(new ColumnOp("or", Arrays.<UnnamedColumn>asList(
                new ColumnOp("or", Arrays.<UnnamedColumn>asList(
                        columnOp1, columnOp2
                )),
                columnOp3
        )));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query20Test() throws VerdictDbException {
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation nation = new BaseTable("tpch", "nation", "n");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("s", "s_name"),
                        new BaseColumn("s", "s_address")
                ),
                Arrays.asList(supplier, nation));
        SelectQueryOp subsubquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new BaseColumn("l", "l_partkey"), "agg_partkey"),
                        new AliasedColumn(new BaseColumn("l", "l_suppkey"), "agg_suppkey"),
                        new AliasedColumn(new ColumnOp("multiply", Arrays.<UnnamedColumn>asList(
                                ConstantColumn.valueOf("0.5"), new ColumnOp("sum", new BaseColumn("l", "l_quantity")))), "agg_quantity")
                ),
                new BaseTable("tpch", "lineitem", "l"));
        subsubquery.addFilterByAnd(new ColumnOp("greaterequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("date", ConstantColumn.valueOf("'1994-01-01'"))
        )));
        subsubquery.addFilterByAnd(new ColumnOp("less", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l", "l_shipdate"),
                new ColumnOp("add", Arrays.<UnnamedColumn>asList(
                        new ColumnOp("date", ConstantColumn.valueOf("'1995-01-01'")),
                        new ColumnOp("interval", Arrays.<UnnamedColumn>asList(ConstantColumn.valueOf("'1'"), ConstantColumn.valueOf("year")))
                ))
        )));
        subsubquery.addGroupby(Arrays.<GroupingAttribute>asList(
                new AliasReference("l_partkey"),
                new AliasReference("l_suppkey")
        ));
        subsubquery.setAliasName("agg_lineitem");
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new BaseColumn("ps", "ps_suppkey")
                ),
                Arrays.asList(new BaseTable("tpch", "partsupp", "ps"), subsubquery));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("agg_lineitem", "agg_partkey"),
                new BaseColumn("ps", "ps_partkey")
        )));
        subquery.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("agg_lineitem", "agg_suppkey"),
                new BaseColumn("ps", "ps_suppkey")
        )));
        SelectQueryOp subsubquery2 = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new BaseColumn("p", "p_partkey")),
                new BaseTable("tpch", "part", "p"));
        subsubquery2.addFilterByAnd(new ColumnOp("like", Arrays.<UnnamedColumn>asList(
                new BaseColumn("p", "p_name"), ConstantColumn.valueOf("'forest%'")
        )));
        //subquery.addFilterByAnd(new ColumnOp("in", Arrays.asList(
        //        new BaseColumn("ps", "ps_partkey"),
        //        SubqueryColumn.getSubqueryColumn(subsubquery2)
        //)));
        subquery.addFilterByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new BaseColumn("ps", "ps_availqty"),
                new BaseColumn("agg_lineitem", "agg_quantity")
        )));
        relation.addFilterByAnd(new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                SubqueryColumn.getSubqueryColumn(subquery)
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_name"),
                ConstantColumn.valueOf("'CANADA'")
        )));
        relation.addOrderby(new OrderbyAttribute("s_name"));
        relation.addLimit(ConstantColumn.valueOf(1));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }

    @Test
    public void Query21Test() throws VerdictDbException {
        AbstractRelation supplier = new BaseTable("tpch", "supplier", "s");
        AbstractRelation lineitem = new BaseTable("tpch", "lineitem", "l1");
        AbstractRelation orders = new BaseTable("tpch", "orders", "o");
        AbstractRelation nation = new BaseTable("tpch", "nation", "n");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("s", "s_name"),
                        new AliasedColumn(new ColumnOp("count"), "numwait")
                ),
                Arrays.asList(supplier, lineitem, orders, nation));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_suppkey"),
                new BaseColumn("l1", "l_suppkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderkey"),
                new BaseColumn("l1", "l_orderkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_orderstatus"),
                ConstantColumn.valueOf("'F'")
        )));
        relation.addFilterByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l1", "l_receiptdate"),
                new BaseColumn("l1", "l_commitdate")
        )));
        SelectQueryOp subquery1 = SelectQueryOp.getSelectQueryOp(Arrays.<SelectItem>asList(
                new AsteriskColumn()
        ), new BaseTable("tpch", "lineitem", "l2"));
        subquery1.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l2", "l_orderkey"),
                new BaseColumn("l1", "l_orderkey")
        )));
        subquery1.addFilterByAnd(new ColumnOp("notequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l2", "l_suppkey"),
                new BaseColumn("l1", "l_suppkey")
        )));
        //relation.addFilterByAnd(new ColumnOp("exists", SubqueryColumn.getSubqueryColumn(subquery1)));
        SelectQueryOp subquery2 = SelectQueryOp.getSelectQueryOp(Arrays.<SelectItem>asList(
                new AsteriskColumn()
        ), new BaseTable("tpch", "lineitem", "l3"));
        subquery2.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l3", "l_orderkey"),
                new BaseColumn("l1", "l_orderkey")
        )));
        subquery2.addFilterByAnd(new ColumnOp("notequal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l3", "l_suppkey"),
                new BaseColumn("l1", "l_suppkey")
        )));
        subquery2.addFilterByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new BaseColumn("l3", "l_receiptdate"),
                new BaseColumn("l3", "l_commitdate")
        )));
        //relation.addFilterByAnd(new ColumnOp("notexists", SubqueryColumn.getSubqueryColumn(subquery2)));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("s", "s_nationkey"),
                new BaseColumn("n", "n_nationkey")
        )));
        relation.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("n", "n_name"),
                ConstantColumn.valueOf("'SAUDI ARABIA'")
        )));
        relation.addGroupby(new AliasReference("s_name"));
        relation.addOrderby(new OrderbyAttribute("numwait", "desc"));
        relation.addOrderby(new OrderbyAttribute("s_name"));
        relation.addLimit(ConstantColumn.valueOf(100));
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(19, result.size());
    }

    @Test
    public void Query22Test() throws VerdictDbException {
        SelectQueryOp subquery = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(
                        new AliasedColumn(new ColumnOp("substr", Arrays.<UnnamedColumn>asList(
                                new BaseColumn("c", "c_phone"),
                                ConstantColumn.valueOf(1), ConstantColumn.valueOf(2))), "cntrycode"),
                        new BaseColumn("c", "c_acctbal")
                ),
                new BaseTable("tpch", "customer", "c"));
        subquery.addFilterByAnd(new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                new ColumnOp("substr", Arrays.<UnnamedColumn>asList(
                        new BaseColumn("c", "c_phone"),
                        ConstantColumn.valueOf(1), ConstantColumn.valueOf(2))),
                ConstantColumn.valueOf("'13'"), ConstantColumn.valueOf("'31'"), ConstantColumn.valueOf("'23'"),
                ConstantColumn.valueOf("'29'"), ConstantColumn.valueOf("'30'"), ConstantColumn.valueOf("'18'"),
                ConstantColumn.valueOf("'17'")
        )));
        SelectQueryOp subsubquery1 = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new ColumnOp("avg", new BaseColumn("c", "c_acctbal"))),
                new BaseTable("tpch", "customer", "c"));
        subsubquery1.addFilterByAnd(new ColumnOp("greater", Arrays.<UnnamedColumn>asList(
                new BaseColumn("c", "c_acctbal"),
                ConstantColumn.valueOf("0.00")
        )));
        subsubquery1.addFilterByAnd(new ColumnOp("in", Arrays.<UnnamedColumn>asList(
                new ColumnOp("substr", Arrays.<UnnamedColumn>asList(
                        new BaseColumn("c", "c_phone"),
                        ConstantColumn.valueOf(1), ConstantColumn.valueOf(2))),
                ConstantColumn.valueOf("'13'"), ConstantColumn.valueOf("'31'"), ConstantColumn.valueOf("'23'"),
                ConstantColumn.valueOf("'29'"), ConstantColumn.valueOf("'30'"), ConstantColumn.valueOf("'18'"),
                ConstantColumn.valueOf("'17'")
        )));
       // subquery.addFilterByAnd(new ColumnOp("greater", Arrays.asList(
       //         new BaseColumn("c", "c_acctbal"), SubqueryColumn.getSubqueryColumn(subsubquery1)
       // )));
        SelectQueryOp subsubquery2 = SelectQueryOp.getSelectQueryOp(
                Arrays.<SelectItem>asList(new AsteriskColumn()),
                new BaseTable("tpch", "orders", "o"));
        subsubquery2.addFilterByAnd(new ColumnOp("equal", Arrays.<UnnamedColumn>asList(
                new BaseColumn("o", "o_custkey"),
                new BaseColumn("c", "c_custkey")
        )));
        //subquery.addFilterByAnd(new ColumnOp("notexists", SubqueryColumn.getSubqueryColumn(subsubquery2)));
        subquery.setAliasName("custsale");
        SelectQueryOp relation = SelectQueryOp.getSelectQueryOp(
                Arrays.asList(
                        new BaseColumn("custsale", "cntrycode"),
                        new AliasedColumn(new ColumnOp("count"), "numcust"),
                        new AliasedColumn(new ColumnOp("sum", new BaseColumn("custsale", "c_acctbal")), "totacctbal")
                ),
                subquery);
        relation.addGroupby(new AliasReference("cntrycode"));
        relation.addOrderby(new OrderbyAttribute("cntrycode"));
        relation.addLimit(ConstantColumn.valueOf(1));
        String expected = "select " +
                "`custsale`.`cntrycode`, " +
                "count(*) as numcust, " +
                "sum(`custsale`.`c_acctbal`) as totacctbal " +
                "from " +
                "(" +
                "select " +
                "substr(`c`.`c_phone`, 1, 2) as cntrycode, " +
                "`c`.`c_acctbal` " +
                "from " +
                "`tpch`.`customer` as c " +
                "where " +
                "(((substr(`c`.`c_phone`, 1, 2)) in " +
                "(':1', ':2', ':3', ':4', ':5', ':6', ':7')) " +
                "and (`c`.`c_acctbal` > (" +
                "select " +
                "avg(`c`.`c_acctbal`) " +
                "from " +
                "`tpch`.`customer` as c " +
                "where " +
                "(`c`.`c_acctbal` > 0.00) " +
                "and ((substr(`c`.`c_phone`, 1, 2)) in " +
                "(':1', ':2', ':3', ':4', ':5', ':6', ':7'))" +
                "))) " +
                "and (not exists (" +
                "select " +
                "* " +
                "from " +
                "`tpch`.`orders` as o " +
                "where " +
                "`o`.`o_custkey` = `c`.`c_custkey`" +
                ")" +
                ")) as custsale " +
                "group by " +
                "`cntrycode` " +
                "order by " +
                "`cntrycode` asc " +
                "limit 1";
        SelectQueryToSql relToSql = new SelectQueryToSql(new HiveSyntax());
        String actual = relToSql.toSql(relation);
        List<Object[]> result = shell.executeStatement(actual);
        assertEquals(1, result.size());
    }
}
