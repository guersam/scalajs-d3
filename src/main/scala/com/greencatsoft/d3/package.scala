package com.greencatsoft

import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.{ GlobalScope, RegExp, UndefOr }
import scala.scalajs.js.Any.{ jsArrayOps, wrapArray }
import scala.scalajs.js.UndefOr.{ any2undefOrA, undefOr2ops }

import org.scalajs.dom
import org.scalajs.dom.{ NodeList, document }
import org.scalajs.dom.ext.Castable
import org.scalajs.dom.raw.Node
import org.scalajs.dom.svg.{ G, Locatable, Matrix, SVG, Stylable }
import org.scalajs.dom.window

import com.greencatsoft.d3.D3Api
import com.greencatsoft.d3.common.{ Point, Transformable }
import com.greencatsoft.d3.common.Bounds
import com.greencatsoft.d3.common.Bounds.svgRect2Bounds
import com.greencatsoft.d3.selection.{ GenericSelection, HtmlSelection, Selection, SvgSelection }

package object d3 {

  object generic extends D3Provider[Node, GenericSelection] {
    trait D3 extends D3Api[Node, GenericSelection]

    implicit override val d3: D3 = global.d3.cast[D3]

    private[d3] override def valid(node: UndefOr[Node]): Boolean = node.isDefined
  }

  object svg extends D3Provider[dom.svg.Element, SvgSelection] {
    trait D3 extends D3Api[dom.svg.Element, SvgSelection]

    implicit override val d3: D3 = global.d3.cast[D3]

    type LocatableElement = dom.svg.Element with Locatable

    type TransformableElement = dom.svg.Element with dom.svg.Transformable

    type StylableElement = dom.svg.Element with Stylable

    implicit class D3LocatableElement(element: LocatableElement) {

      implicit def viewNode: SVG = {
        (element.tagName match {
          case "svg" => element
          case _ => element.viewportElement
        }).cast[SVG]
      }

      def toLocalBounds(fromElem: LocatableElement): Bounds =
        toLocalCoords(fromElem.getBBox, fromElem)

      def toLocalLocation(fromElem: LocatableElement): Point =
        toLocalBounds(fromElem).location

      def toLocalCoords[A <: Transformable[A]](transformable: A): A =
        transformable.matrixTransform(element.getScreenCTM.inverse)

      def toLocalCoords[A <: Transformable[A]](transformable: A, fromElem: LocatableElement): A =
        transformable.matrixTransform(getTransformToElementFix(fromElem).inverse)

      def getTransformToElementFix(toElem: dom.svg.Element with LocatableElement): Matrix = {
        val isWebkit = RegExp("WebKit").test(window.navigator.userAgent.toString)

        if (isWebkit) {
          // TODO Workaround for Webkit bug #86010
          toElem.getScreenCTM.inverse.multiply(element.getScreenCTM)
        } else {
          /*
       * TODO Workaround for "Error: Argument 1 of SVGGraphicsElement.getTransformToElement does not implement interface SVGGraphicsElement."
       * when calling getScreenCTM or getTransformToElementon of a clip-path element on Firefox.
       */
          toElem.tagName match {
            case "clipPath" =>
              val parent = toElem.parentNode

              val clone = {
                val g = document.createElementNS("http://www.w3.org/2000/svg", "g")
                parent.insertBefore(g, toElem).cast[G]
              }

              Option(toElem.cast[dom.svg.Transformable].transform.baseVal.consolidate).foreach(clone.transform.baseVal.initialize)

              val matrix = element.getTransformToElement(clone)
              parent.removeChild(clone)
              matrix
            case _ =>
              element.getTransformToElement(toElem)
          }
        }
      }
    }

    private[d3] override def valid(node: UndefOr[Node]): Boolean =
      node.map(_.namespaceURI).filter(_ != null).exists(_.endsWith("svg"))
  }

  object html extends D3Provider[dom.html.Element, HtmlSelection] {
    trait D3 extends D3Api[dom.html.Element, HtmlSelection]

    implicit override val d3: D3 = global.d3.cast[D3]

    private[d3] override def valid(node: UndefOr[Node]): Boolean =
      node.map(_.namespaceURI).filter(_ != null).exists(_.endsWith("html"))
  }

  private[d3] object global extends GlobalScope {
    val d3: D3Api[_, _] = js.native
  }

  private[d3] trait D3Provider[A <: Node, B <: Selection[A, B]] {

    implicit val d3: D3Api[A, B]

    implicit def node2selection(elem: A): B = d3.select(elem)

    implicit class D3NodeResult(selection: B) {

      def node: Option[A] = selection.head.headOption.filter(_ != null)

      def nodes: Seq[A] = selection.head
    }

    implicit class D3Element(element: A) {

      def addClass(cls: String): A = {
        d3.select(element).classed(js.Dictionary(cls -> true))
        element
      }

      def removeClass(cls: String): A = {
        d3.select(element).classed(js.Dictionary(cls -> false))
        element
      }

      def hasClass(cls: String): Boolean = d3.select(element).classed(cls)

      def closest(filter: A => Boolean): Option[A] = {
        require(filter != null, "Missing argument 'filter'.")

        def search(node: Node): Option[A] = {
          val result = Some(node) collect {
            case n if n.nodeType == 1 && valid(n) => n.cast[A]
          }

          result.find(filter) match {
            case Some(e) => Some(e)
            case None => Option(node.parentNode).map(search(_)).flatten
          }
        }

        search(element)
      }

      def appendClone[T <: A](elem: T, deepCopy: Boolean = true): T = {
        require(elem != null, "Missing argument 'elem'.")

        def removeId(e: A) {
          d3.select(e).attr("id", null)
          e.childNodes.foreach(removeId(_))
        }

        val clone = elem.cloneNode(deepCopy).cast[A]
        removeId(clone)

        element.insertBefore(clone, elem).cast[T]
      }
    }

    implicit class D3ElementList(list: NodeList) extends Traversable[A] {

      override def foreach[U](f: A => U): Unit =
        for (i <- 0 until list.length; item = list.item(i) if valid(item)) f(item.cast[A])
    }

    private[d3] def valid(node: UndefOr[Node]): Boolean
  }
}
