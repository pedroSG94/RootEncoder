package com.github.faucamp.simplertmp.amf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okio.BufferedSink;
import okio.BufferedSource;

/**
 * AMF Array
 * 
 * @author francois, yuhsuan.lin
 */
public class AmfArray implements AmfData {

    private List<AmfData> items;
    private int size = -1;

    @Override
    public void writeTo(BufferedSink out) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void readFrom(BufferedSource in) throws IOException {
        // Skip data type byte (we assume it's already read)
        int length = in.readInt();
        size = 5; // 1 + 4
        items = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            AmfData dataItem = AmfDecoder.readFrom(in);
            size += dataItem.getSize();
            items.add(dataItem);
        }
    }

    @Override
    public int getSize() {
        if (size == -1) {
            size = 5; // 1 + 4
            if (items != null) {
                for (AmfData dataItem : items) {
                    size += dataItem.getSize();
                }
            }
        }
        return size;
    }

    /** @return the amount of items in this the array */
    public int getLength() {
        return items != null ? items.size() : 0;
    }

    public List<AmfData> getItems() {
        if (items == null) {
            items = Collections.emptyList();
        }
        return items;
    }

    public void addItem(AmfData dataItem) {
        getItems().add(this);
    }
}
