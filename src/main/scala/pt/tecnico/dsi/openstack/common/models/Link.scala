package pt.tecnico.dsi.openstack.common.models

import cats.derived
import cats.Show
import io.circe.{Codec, Decoder, DecodingFailure, HCursor}
import io.circe.derivation.deriveCodec
import org.http4s.Uri
import org.http4s.circe.{decodeUri, encodeUri}

object Link {
  implicit val codec: Codec.AsObject[Link] = deriveCodec

  // TODO: is this the right place for this decoder?
  implicit val linksDecoder: Decoder[List[Link]] = { cursor: HCursor =>
    // Openstack has two ways to represent links (because why not):
    // This one is mostly used in Keystone and Designate
    //   "links": {
    //     "self": "http://example.com/identity/v3/role_assignments",
    //     "previous": null,
    //     "next": null
    //   }
    // This one is used everywhere else
    //   "links": [
    //     {
    //       "href": "http://127.0.0.1:33951/v3/89afd400-b646-4bbc-b12b-c0a4d63e5bd3/volumes/2b955850-f177-45f7-9f49-ecb2c256d161",
    //       "rel": "self"
    //     }, {
    //       "href": "http://127.0.0.1:33951/89afd400-b646-4bbc-b12b-c0a4d63e5bd3/volumes/2b955850-f177-45f7-9f49-ecb2c256d161",
    //       "rel": "bookmark"
    //     }
    //   ]
    val value = cursor.value
    if (value.isArray) Decoder.decodeList[Link].apply(cursor) // This cursor.as[List[Link]] would lead to a stack overflow
    else if (value.isObject) value.dropNullValues.as[Map[String, Uri]].map(_.map{ case (rel, uri) => Link(rel, uri) }.toList)
    else Left(DecodingFailure("Links can only be a object or array.", cursor.history))
  }
  
  implicit val showFoo: Show[Link] = {
    import derived.auto.show._
    derived.semiauto.show
  }
}
case class Link(rel: String, href: Uri)