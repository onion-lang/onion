package onion.compiler

import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedHashSet

import scala.jdk.CollectionConverters.*

private[onion] final class ExplicitClasspathClassLoader(
  urls: Array[URL],
  parent: ClassLoader
) extends URLClassLoader(urls, parent):
  override protected def loadClass(name: String, resolve: Boolean): Class[?] =
    getClassLoadingLock(name).synchronized {
      var loaded = findLoadedClass(name)
      if loaded == null then
        loaded =
          if parentFirst(name) then super.loadClass(name, false)
          else
            try findClass(name)
            catch
              case _: ClassNotFoundException => super.loadClass(name, false)
      if resolve then resolveClass(loaded)
      loaded
    }

  override def getResource(name: String): URL =
    Option(findResource(name)).getOrElse {
      val parent = getParent
      if parent == null then ClassLoader.getSystemResource(name)
      else parent.getResource(name)
    }

  override def getResources(name: String): Enumeration[URL] =
    val resources = LinkedHashSet[URL]()
    findResources(name).asScala.foreach(resources.add)
    val parent = getParent
    val inherited =
      if parent == null then ClassLoader.getSystemResources(name)
      else parent.getResources(name)
    inherited.asScala.foreach(resources.add)
    Collections.enumeration(resources)

  private def parentFirst(name: String): Boolean =
    ExplicitClasspathClassLoader.ParentFirstPrefixes.exists(name.startsWith)

private[onion] object ExplicitClasspathClassLoader:
  private val ParentFirstPrefixes =
    Vector("java.", "javax.", "jdk.", "sun.", "com.sun.")
