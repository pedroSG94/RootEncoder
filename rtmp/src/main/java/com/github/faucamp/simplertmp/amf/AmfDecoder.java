package com.github.faucamp.simplertmp.amf;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author francois
 */
public class AmfDecoder {

  public static AmfData readFrom(InputStream in) throws IOException {

    byte amfTypeByte = (byte) in.read();
    AmfType amfType = AmfType.valueOf(amfTypeByte);

    AmfData amfData;
    if (amfType != null) {
      switch (amfType) {
        case NUMBER:
          amfData = new AmfNumber();
          break;
        case BOOLEAN:
          amfData = new AmfBoolean();
          break;
        case STRING:
          amfData = new AmfString();
          break;
        case OBJECT:
          amfData = new AmfObject();
          break;
        case NULL:
          return new AmfNull();
        case UNDEFINED:
          return new AmfUndefined();
        case ECMA_MAP:
          amfData = new AmfMap();
          break;
        case STRICT_ARRAY:
          amfData = new AmfArray();
          break;
        default:
          throw new IOException("Unknown/unimplemented AMF data type: " + amfType);
      }
    } else {
      //If you can see -1 here it is because server close connection before library can read AMF packet.
      throw new IOException("Unknown AMF data type: " + amfTypeByte);
    }

    amfData.readFrom(in);
    return amfData;
  }
}
