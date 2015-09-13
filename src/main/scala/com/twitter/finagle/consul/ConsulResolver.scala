package com.twitter.finagle.consul

import java.net.{InetSocketAddress, SocketAddress}

import com.twitter.finagle.util.{DefaultTimer, Updater}
import com.twitter.finagle.{Addr, Resolver}
import com.twitter.util.{FuturePool, Var}

class ConsulResolver extends Resolver {
  val scheme = "consul"

  private[this] val timer      = DefaultTimer.twitter
  private[this] val futurePool = FuturePool.unboundedPool

  private[this] def addresses(hosts: String, name: String, digest: String) : (String, Option[Set[SocketAddress]]) = {
    val services  = ConsulServiceFactory.getService(hosts).list(name)
    val newDigest = services.map(_.sessionId).sorted.mkString(",")
    if (newDigest != digest) {
      val newAddrs = services.map{ s =>
        new InetSocketAddress(s.address, s.port).asInstanceOf[SocketAddress]
      }.toSet

      println(newAddrs)
      (newDigest, Some(newAddrs))
    } else {
      (newDigest, None)
    }
  }

  def addrOf(hosts: String, query: ConsulQuery): Var[Addr] =
    Var.async(Addr.Pending: Addr) { u =>
      val (digest, maybeAddrs) = addresses(hosts, query.name, "")
      maybeAddrs foreach { addrs =>
        u() = Addr.Bound(addrs)
      }

      val updater = new Updater[Unit] {
        val one     = Seq(())
        var _digest = digest
        protected def preprocess(elems: Seq[Unit]) = one
        protected def handle(unit: Unit) {
          addresses(hosts, query.name, _digest) match {
            case (newDigest, Some(addrs)) =>
              u() = Addr.Bound(addrs)
              _digest = newDigest
            case (newDigest, None) =>
              _digest = newDigest
          }
        }
      }
      timer.schedule(query.ttl.fromNow, query.ttl) {
        futurePool(updater(()))
      }
    }

  def bind(arg: String): Var[Addr] = arg.split("!") match {
    case Array(hosts, query) =>
      ConsulQuery.decodeString(query) match {
        case Some(q) => addrOf(hosts, q)
        case None =>
          throw new IllegalArgumentException(s"Invalid address '$arg'")
      }

    case _ =>
      throw new IllegalArgumentException(s"Invalid address '$arg'")
  }
}
