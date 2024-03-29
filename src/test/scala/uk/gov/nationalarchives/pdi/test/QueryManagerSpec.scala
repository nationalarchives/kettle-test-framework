/*
 * Copyright (c) 2021 The National Archives
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.gov.nationalarchives.pdi.test

import com.google.common.collect.Lists.newArrayList
import org.apache.jena.atlas.lib.StrUtils
import org.apache.jena.query.{ QuerySolution, ResultSet, ResultSetFactory, ResultSetRewindable }
import org.apache.jena.rdf.model.{ Model, RDFNode, ResourceFactory }
import org.apache.jena.riot.RiotException
import org.apache.jena.sparql.resultset.ResultSetCompare
import org.apache.jena.sparql.sse.{ Item, SSE }
import org.mockito.Mockito.when
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.apache.jena.sparql.sse.builders.BuilderResultSet

import java.nio.file.Paths
import java.util
import scala.util.Try

class QueryManagerSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "QueryManager.executeQuery" must {
    "return a Success with a count value" in {
      val exampleRdfFile = Paths.get(this.getClass.getClassLoader.getResource("example.ttl").toURI)
      val exampleRdfParentDir = exampleRdfFile.getParent
      val sparqlQuery =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?policy ?label WHERE { ?policy rdfs:label ?label . }"
      val result = QueryManager.executeQuery(sparqlQuery, exampleRdfParentDir, List("example"), ".ttl")
      result.success.value must be(2)
    }
    "return a Success getting a RecordSet" in {
      val exampleRdfFile = Paths.get(this.getClass.getClassLoader.getResource("example.ttl").toURI)
      val exampleRdfParentDir = exampleRdfFile.getParent
      val sparqlQuery =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?policy ?label WHERE { ?policy rdfs:label ?label . }"
      val result: Try[QueryManager.RecordSet] =
        QueryManager.executeQueryAndGetRecordSet(sparqlQuery, exampleRdfParentDir, List("example"), ".ttl")

      // assert size
      result.success.value.contents.size must be(2)

      // validate Row 1
      val resultRow1 = result.get.contents(0)
      val value1: RDFNode = resultRow1.get("policy").get
      value1.asResource must be(
        ResourceFactory.createResource("http://cat.nationalarchives.gov.uk/policy.Retained_Until_1996")
      )
      val value2: RDFNode = resultRow1.get("label").get
      value2.asLiteral.getString must be("Retained Until 1996")
      // validate Row 2
      val resultRow2 = result.get.contents(1)
      val value3: RDFNode = resultRow2.get("policy").get
      value3.asResource() must be(
        ResourceFactory.createResource("http://cat.nationalarchives.gov.uk/policy.Open_on_Transfer_1991-12-31")
      )
      val value4: RDFNode = resultRow2.get("label").get
      value4.asLiteral.getString must be("Open on Transfer 1991-12-31")
    }
    "return a Success getting a ResultSet" in {
      val exampleRdfFile = Paths.get(this.getClass.getClassLoader.getResource("example.ttl").toURI)
      val exampleRdfParentDir = exampleRdfFile.getParent
      val sparqlQuery =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?policy ?label WHERE { ?policy rdfs:label ?label . }"
      val result: Try[ResultSetRewindable] =
        QueryManager.executeQueryAndGetResultSet(sparqlQuery, exampleRdfParentDir, List("example"), ".ttl")

      // assert size
      result.success.value.size must be(2)

      val resultSetStrings: util.List[String] = util.Arrays.asList(
        "(resultset (?policy ?label)",
        " (row (?policy <http://cat.nationalarchives.gov.uk/policy.Retained_Until_1996>) (?label \"Retained Until 1996\"))",
        " (row (?policy <http://cat.nationalarchives.gov.uk/policy.Open_on_Transfer_1991-12-31>) (?label \"Open on Transfer 1991-12-31\"))",
        ")"
      );

      val expectedResultSet: ResultSetRewindable = createResultSet(resultSetStrings)

      ResultSetCompare.equalsByValue(result.success.value, expectedResultSet) must be(true)
    }

    def createResultSet(strings: util.List[String]) = {
      if (strings.size() == 0) throw new IllegalArgumentException
      val x = StrUtils.strjoinNL(strings)
      val item = SSE.parse(x)
      ResultSetFactory.makeRewindable(BuilderResultSet.build(item))
    }

    "return a Success getting a RecordSet with no results when the query yields 0 results" in {
      val exampleRdfFile = Paths.get(this.getClass.getClassLoader.getResource("example.ttl").toURI)
      val exampleRdfParentDir = exampleRdfFile.getParent
      val sparqlQuery =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?policy ?label WHERE { ?policy rdfs:label2 ?label . }"
      val result =
        QueryManager.executeQueryAndGetRecordSet(sparqlQuery, exampleRdfParentDir, List("example"), ".ttl")

      // assert size
      result.success.value.contents.size must be(0)
    }
    "return a Failure with an Exception" in {
      val sparqlQuery =
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?policy ?label WHERE { ?policy rdfs:label ?label . }"
      val result = QueryManager.executeQuery(sparqlQuery, Paths.get("."), List(""), "")
      result.failure.exception mustBe a[RiotException]
    }
    "create and query a combined graph from two RDF files" in {
      val exampleRdfFile = Paths.get(this.getClass.getClassLoader.getResource("output-record-descriptions.ttl").toURI)
      val exampleRdfParentDir = exampleRdfFile.getParent
      val sparqlQuery =
        s"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |PREFIX dct: <http://purl.org/dc/terms/>
           |PREFIX odrl: <http://www.w3.org/ns/odrl/2/>
           |SELECT ?policy ?label WHERE {
           |?item dct:accessRights ?rights .
           |?rights odrl:hasPolicy ?policy .
           |?policy a odrl:Policy .
        }""".stripMargin
      val result = QueryManager
        .executeQuery(sparqlQuery, exampleRdfParentDir, List("output-record-descriptions", "policies"), ".ttl")
      result.success.value must be(1)
    }
    "create and query a combined graph from three RDF files" in {
      val exampleRdfFile = Paths.get(this.getClass.getClassLoader.getResource("output-record-descriptions.ttl").toURI)
      val exampleRdfParentDir = exampleRdfFile.getParent
      val sparqlQuery =
        s"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |PREFIX dct: <http://purl.org/dc/terms/>
           |PREFIX odrl: <http://www.w3.org/ns/odrl/2/>
           |PREFIX ver: <http://purl.org/linked-data/version#>
           |PREFIX cat: <http://cat.nationalarchives.gov.uk/>
           |SELECT ?policy WHERE {
           |?concept dct:type cat:record-concept ;
           |         ver:currentVersion ?description .
           |?description dct:accessRights ?rights .
           |?rights odrl:hasPolicy ?policy .
           |?policy a odrl:Policy .
        }""".stripMargin
      val result = QueryManager
        .executeQuery(
          sparqlQuery,
          exampleRdfParentDir,
          List("output-record-descriptions", "output-record-concepts", "policies"),
          ".ttl"
        )
      result.success.value must be(1)
    }
  }

  "QueryManager.parseResultSet" must {
    "return a non-empty RecordSet when ResultSet contains at least a row" in {

      val mockResultSet = mock[ResultSet]
      val mockQuerySolution = mock[QuerySolution]
      val mockQuerySolution2 = mock[QuerySolution]
      val mockRDFNodeVal1 = mock[RDFNode]
      val mockRDFNodeVal2 = mock[RDFNode]
      val mockRDFNodeVal3 = mock[RDFNode]
      val mockRDFNodeVal4 = mock[RDFNode]

      when(mockResultSet.hasNext) thenReturn true thenReturn true thenReturn false
      when(mockResultSet.getResultVars) thenReturn newArrayList("col1", "col2")
      when(mockResultSet.nextSolution()) thenReturn mockQuerySolution thenReturn mockQuerySolution2
      when(mockResultSet.next()) thenReturn mockQuerySolution thenReturn mockQuerySolution2
      when(mockQuerySolution.get("col1")) thenReturn mockRDFNodeVal1
      when(mockQuerySolution.get("col2")) thenReturn mockRDFNodeVal2
      when(mockRDFNodeVal1.asLiteral()) thenReturn ResourceFactory.createPlainLiteral("val1")
      when(mockRDFNodeVal2.asLiteral()) thenReturn ResourceFactory.createPlainLiteral("val2")
      when(mockQuerySolution2.get("col1")) thenReturn mockRDFNodeVal3
      when(mockQuerySolution2.get("col2")) thenReturn mockRDFNodeVal4
      when(mockRDFNodeVal3.asLiteral()) thenReturn ResourceFactory.createPlainLiteral("val3")
      when(mockRDFNodeVal4.asLiteral()) thenReturn ResourceFactory.createPlainLiteral("val4")

      val result = QueryManager.parseResultSet(mockResultSet)

      result.contents.size must be(2)
      // validate Row 1
      val resultRow1 = result.contents(0)
      val value1: RDFNode = resultRow1.get("col1").get
      value1.asLiteral.getString must be("val1")
      val value2: RDFNode = resultRow1.get("col2").get
      value2.asLiteral.getString must be("val2")
      // validate Row 2
      val resultRow2 = result.contents(1)
      val value3: RDFNode = resultRow2.get("col1").get
      value3.asLiteral.getString must be("val3")
      val value4: RDFNode = resultRow2.get("col2").get
      value4.asLiteral.getString must be("val4")
    }

    "return a empty RecordSet when ResultSet contains no rows" in {

      val mockResultSet = mock[ResultSet]

      when(mockResultSet.hasNext) thenReturn false
      when(mockResultSet.getResultVars) thenReturn null

      val result = QueryManager.parseResultSet(mockResultSet)

      result.contents.size must be(0)

    }
  }

}
