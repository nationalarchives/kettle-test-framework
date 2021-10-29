import org.apache.commons.io.FileUtils
import org.pentaho.di.trans.step.StepMetaInterface
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.nationalarchives.pdi.step.jena.model.JenaModelStepMeta
import uk.gov.nationalarchives.pdi.step.jena.serializer.JenaSerializerStepMeta
import uk.gov.nationalarchives.pdi.step.jena.shacl.JenaShaclStepMeta
import uk.gov.nationalarchives.pentaho.{ DatabaseManager, QueryManager, WorkflowManager }

import java.io.File
import java.nio.file.Paths
import scala.reflect.io.Directory
import scala.util.{ Failure, Success }

class ExampleWorkflowSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val exampleWorkflow = "example.ktr"
  private val outputDirectory = "output"
  private val resultFilenamePrefix = "example"
  private val resultFilenameSuffix = ".ttl"
  private val shaclDirectory = new File("shacl")
  private val shaclDirectoryPath = shaclDirectory.getAbsolutePath
  private val shaclFilename = "odrl-shacl.ttl"
  private val databaseDir = "./data-dir"
  private val databaseName = "test-db"
  private val databaseUrl = s"jdbc:h2:$databaseDir/$databaseName;database_to_upper=false;mode=MSSQLServer"
  private val databaseManager = DatabaseManager(databaseUrl)

  private val plugins: List[Class[_ <: StepMetaInterface]] = List(
    classOf[JenaModelStepMeta],
    classOf[JenaSerializerStepMeta],
    classOf[JenaShaclStepMeta]
  )

  "With the given data, the example workflow" must {
    "produce an RDF result file containing exactly two rdfs:label properties" in {
      val sql: String = {
        """
          |create table tbl_closuretype(closure_type char(1), cltype_desc varchar(255));
          |insert into tbl_closuretype values ('A','Open on Transfer');
          |insert into tbl_closuretype values ('D','Retained Until');
          |create table tbl_item(closure_type char(1), closure_code int, last_date int, open_date datetime);
          |insert into tbl_item values('A', 0, 19911231, null);
          |insert into tbl_item values('D',1996, null, null)
       """.stripMargin
      }
      insertData(sql)

      val params =
        Map(
          "OUTPUT_FILEPATH" -> outputDirectory,
          "OUTPUT_FILENAME" -> s"$resultFilenamePrefix$resultFilenameSuffix",
          "SHACL_DIRECTORY" -> shaclDirectoryPath,
          "SHACL_FILENAME"  -> shaclFilename
        )
      val is = this.getClass.getClassLoader.getResourceAsStream(exampleWorkflow)
      val _ = WorkflowManager.runTransformation(is, "src/test/resources", Some(params), Some(plugins))
      is.close()
      val result = QueryManager.executeQuery(
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?policy ?label WHERE { ?policy rdfs:label ?label. }",
        outputDirectory,
        resultFilenamePrefix,
        resultFilenameSuffix
      )
      result mustBe Success(2)
    }
  }

  private def insertData(sql: String): Unit =
    databaseManager.executeSqlScript(sql) match {
      case Success(_)         => ()
      case Failure(exception) => fail(s"Unable to insert data due to ${exception.getMessage}")
    }

  override def beforeAll(): Unit = {
    clearDatabaseDataDir()
    deleteTestFiles()
  }

  override def afterAll() {
    clearDatabaseDataDir()
    deleteTestFiles()
  }

  private def clearDatabaseDataDir(): Unit =
    new Directory(new File(databaseDir)).deleteRecursively()

  private def deleteTestFiles(): Unit =
    FileUtils.deleteDirectory(new File(outputDirectory))

}
