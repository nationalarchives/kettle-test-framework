package uk.gov.nationalarchives.pdi.test

import org.pentaho.di.core.KettleEnvironment
import org.pentaho.di.core.compress.{CompressionPluginType, NoneCompressionProvider}
import org.pentaho.di.core.plugins.{PluginTypeInterface, StepPluginType}
import org.pentaho.di.core.variables.Variables
import org.pentaho.di.trans.step.StepMetaInterface
import org.pentaho.di.trans.{Trans, TransMeta}

import java.io.InputStream
import java.nio.file.Path
import scala.jdk.CollectionConverters._

/**
  * Used to execute Pentaho Kettle transformations during testing via the Java API
  */
object WorkflowManager {

  /**
    * Executes a Pentaho Kettle transformation with the option of parameters and plugins
    * @param transformationIs the InputStream with the Kettle transformation
    * @param workingDirectory the working directory for the transformation
    * @param maybeParameters an optional map of transformation parameters
    * @param maybePlugins an optional list of plugin classes to be used
    * @return
    */
  def runTransformation(
    transformationIs: InputStream,
    workingDirectory: Path,
    maybeParameters: Option[Map[String, String]],
    maybePlugins: Option[List[Class[_ <: StepMetaInterface]]]): Either[Throwable, Boolean] =
    try {
      val pluginTypes = maybePlugins match {
        case Some(plugins) => setUpPlugins(plugins)
        case _             => setUpPlugins(List())
      }

      // init the environment with plugins
      KettleEnvironment.init(pluginTypes.asJava, true)

      // set the parameters for the transformation
      val vars = new Variables()
      vars.setVariable("Internal.Entry.Current.Directory", workingDirectory.toAbsolutePath.toString)
      val trans = new Trans(new TransMeta(transformationIs, null, false, vars, null))

      maybeParameters match {
        case Some(params) =>
          for (param <- params) {
            trans.setParameterValue(param._1, param._2)
          }
        case _ =>
      }

      // run the transformation
      trans.execute(null)
      trans.waitUntilFinished()
      val result = trans.getResult
      KettleEnvironment.shutdown()
      Right(result.getResult)
    } catch {
      case ex: Throwable => Left(ex)
    }

  private def setUpPlugins(plugins: List[Class[_ <: StepMetaInterface]]): List[PluginTypeInterface] = {
    val stepPluginType = StepPluginType.getInstance()
    val compressionPluginType = CompressionPluginType.getInstance()
    compressionPluginType
      .registerCustom(classOf[NoneCompressionProvider], "compression", "COMPRESSION", "Compression", "", null)
    val stepRegister = registerStepPlugin(stepPluginType) _
    for (plugin <- plugins) {
      stepRegister(plugin)
    }
    List(stepPluginType, compressionPluginType)
  }

  private def registerStepPlugin(register: StepPluginType)(pluginClass: Class[_ <: StepMetaInterface]): Unit = {
    val stepAnnotation = pluginClass.getAnnotation(classOf[org.pentaho.di.core.annotations.Step])
    register.registerCustom(
      pluginClass,
      stepAnnotation.categoryDescription(),
      stepAnnotation.id(),
      stepAnnotation.name(),
      stepAnnotation.description(),
      stepAnnotation.image())
  }

}