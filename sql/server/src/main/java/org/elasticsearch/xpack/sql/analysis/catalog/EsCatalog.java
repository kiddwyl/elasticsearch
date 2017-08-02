/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.analysis.catalog;

import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class EsCatalog implements Catalog {

    private final Supplier<ClusterState> clusterState;
    private IndexNameExpressionResolver indexNameExpressionResolver;

    public EsCatalog(Supplier<ClusterState> clusterState) {
        this.clusterState = clusterState;
    }

    // initialization hack
    public void setIndexNameExpressionResolver(IndexNameExpressionResolver resolver) {
        this.indexNameExpressionResolver = resolver;
    }

    private MetaData metadata() {
        return clusterState.get().getMetaData();
    }

    @Override
    public EsIndex getIndex(String index) {
        if (!indexExists(index)) {
            return EsIndex.NOT_FOUND;
        }
        return EsIndex.build(metadata().index(index));
    }

    @Override
    public boolean indexExists(String index) {
        IndexMetaData idx = metadata().index(index);
        return idx != null;
    }

    @Override
    public boolean indexIsValid(String index) {
        IndexMetaData idx = metadata().index(index);
        return idx != null && indexHasOnlyOneType(idx);
    }

    @Override
    public List<EsIndex> listIndices() {
        return listIndices(null);
    }

    @Override
    public List<EsIndex> listIndices(String pattern) {
        Iterator<IndexMetaData> indexMetadata = null;
        MetaData md = metadata();
        if (pattern == null) {
            indexMetadata = md.indices().valuesIt();
        }
        else {
            String[] indexNames = resolveIndex(pattern);
            List<IndexMetaData> indices = new ArrayList<>(indexNames.length);
            for (String indexName : indexNames) {
                 indices.add(md.index(indexName));    
            }
            indexMetadata = indices.iterator();
        }

        List<EsIndex> list = new ArrayList<>();
        // filter unsupported (indices with more than one type) indices
        while (indexMetadata.hasNext()) {
            IndexMetaData imd = indexMetadata.next();
            if (indexHasOnlyOneType(imd)) {
                list.add(EsIndex.build(imd));
            }
        }

        return list;
    }

    private boolean indexHasOnlyOneType(IndexMetaData index) {
        return index.getMappings().size() <= 1;
    }

    private String[] resolveIndex(String pattern) {
        return indexNameExpressionResolver.concreteIndexNames(clusterState.get(), IndicesOptions.strictExpandOpenAndForbidClosed(), pattern);
    }
}