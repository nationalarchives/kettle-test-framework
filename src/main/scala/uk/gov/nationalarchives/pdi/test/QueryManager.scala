package uk.gov.nationalarchives.pdi.test

import org.apache.jena.query._
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot._
import uk.gov.nationalarchives.pdi.test.helpers.IOHelper.findFile

import java.nio.file.Path
import scala.util.{Failure, Success, Try, Using}

/**
  * Used to execute SPARQL queries against RDF files output by Pentaho Kettle during transformation testing
  */
object QueryManager {

  /**
    * Executes the given SPARQL query against the given RDF file and returns the size of the result if successful or an error on failure
    * @param sparqlString the SPARQL query
    * @param rdfDirectory the RDF output directory
    * @param rdfFilenamePrefix the prefix of the RDF output filename
    * @param rdfFilenameSuffix the suffix of the RDF output filename
    * @return
    */
  def executeQuery(
    sparqlString: String,
    rdfDirectory: Path,
    rdfFilenamePrefix: String,
    rdfFilenameSuffix: String): Try[Int] =
    buildQueryAndModel(sparqlString, getRdfFilename(rdfDirectory, rdfFilenamePrefix, rdfFilenameSuffix)) match {
      case Success((query, model)) =>
        Using(QueryExecutionFactory.create(query, model)) { queryExec =>
          val results = queryExec.execSelect
          val rs = ResultSetFactory.copyResults(results)
          rs.size()
        }
      case Failure(e) => Failure(e)
    }

  private def buildQueryAndModel(sparqlString: String, rdfFilename: String): Try[(Query, Model)] = {
    val query = Try(QueryFactory.create(sparqlString))
    val model = Try(RDFDataMgr.loadModel(rdfFilename))
    query.flatMap(q => model.map(m => (q, m)))
  }

  private def getRdfFilename(directory: Path, prefix: String, suffix: String): String = {
    findFile(directory, prefix, suffix) match {
      case Success(Some(file)) => file.getFileName.toString
      case _ => ""
    }
  }
}