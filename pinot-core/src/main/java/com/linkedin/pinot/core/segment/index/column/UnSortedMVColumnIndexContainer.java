package com.linkedin.pinot.core.segment.index.column;

import com.linkedin.pinot.core.index.reader.DataFileReader;
import com.linkedin.pinot.core.segment.index.BitmapInvertedIndexReader;
import com.linkedin.pinot.core.segment.index.ColumnMetadata;
import com.linkedin.pinot.core.segment.index.InvertedIndexReader;
import com.linkedin.pinot.core.segment.index.readers.FixedBitCompressedMVForwardIndexReader;
import com.linkedin.pinot.core.segment.index.readers.ImmutableDictionaryReader;


public class UnSortedMVColumnIndexContainer extends ColumnIndexContainer {

  private final String column;
  private final ColumnMetadata columnMetadata;
  private final FixedBitCompressedMVForwardIndexReader indexReader;
  private final ImmutableDictionaryReader dictionary;
  private final BitmapInvertedIndexReader invertedIndexReader;

  public UnSortedMVColumnIndexContainer(String column, ColumnMetadata columnMetadata,
      FixedBitCompressedMVForwardIndexReader indexReader, ImmutableDictionaryReader dictionary) {
    this(column, columnMetadata, indexReader, dictionary, null);
  }

  public UnSortedMVColumnIndexContainer(String column, ColumnMetadata columnMetadata,
      FixedBitCompressedMVForwardIndexReader indexReader, ImmutableDictionaryReader dictionary,
      BitmapInvertedIndexReader invertedIndex) {
    this.column = column;
    this.columnMetadata = columnMetadata;
    this.indexReader = indexReader;
    this.dictionary = dictionary;
    this.invertedIndexReader = invertedIndex;
  }

  @Override
  public InvertedIndexReader getInvertedIndex() {
    return invertedIndexReader;
  }

  @Override
  public DataFileReader getForwardIndex() {
    return indexReader;
  }

  @Override
  public ImmutableDictionaryReader getDictionary() {
    return dictionary;
  }

  @Override
  public ColumnMetadata getColumnMetadata() {
    return columnMetadata;
  }

  @Override
  public boolean unload() throws Exception {
    indexReader.close();
    dictionary.close();
    if (invertedIndexReader != null) {
      invertedIndexReader.close();
    }
    return true;
  }

}