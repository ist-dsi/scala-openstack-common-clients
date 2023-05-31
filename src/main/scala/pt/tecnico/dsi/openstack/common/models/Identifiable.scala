package pt.tecnico.dsi.openstack.common.models

import org.http4s.Uri

trait Identifiable:
  // All Openstack IDs are strings, 99% are random UUIDs
  def id: String
  def links: List[Link]

  lazy val linksMap: Map[String, Uri] = links.map(l => (l.rel, l.href)).toMap
