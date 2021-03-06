/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.store.graph.v1;

import org.apache.atlas.AtlasErrorCode;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.TimeBoundary;
import org.apache.atlas.model.instance.AtlasClassification;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityExtInfo;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.model.instance.AtlasRelatedObjectId;
import org.apache.atlas.model.instance.AtlasRelationship;
import org.apache.atlas.model.instance.AtlasStruct;
import org.apache.atlas.model.typedef.AtlasRelationshipDef;
import org.apache.atlas.model.typedef.AtlasRelationshipDef.PropagateTags;
import org.apache.atlas.model.typedef.AtlasRelationshipEndDef;
import org.apache.atlas.model.typedef.AtlasStructDef.AtlasAttributeDef;
import org.apache.atlas.repository.Constants;
import org.apache.atlas.repository.RepositoryException;
import org.apache.atlas.repository.graph.GraphHelper;
import org.apache.atlas.repository.graphdb.AtlasEdge;
import org.apache.atlas.repository.graphdb.AtlasEdgeDirection;
import org.apache.atlas.repository.graphdb.AtlasElement;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.type.AtlasArrayType;
import org.apache.atlas.type.AtlasClassificationType;
import org.apache.atlas.type.AtlasEntityType;
import org.apache.atlas.type.AtlasMapType;
import org.apache.atlas.type.AtlasRelationshipType;
import org.apache.atlas.type.AtlasStructType;
import org.apache.atlas.type.AtlasStructType.AtlasAttribute;
import org.apache.atlas.type.AtlasType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.atlas.type.AtlasTypeUtil;
import org.apache.atlas.utils.AtlasJson;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.atlas.model.typedef.AtlasBaseTypeDef.*;
import static org.apache.atlas.repository.Constants.*;
import static org.apache.atlas.repository.graph.GraphHelper.EDGE_LABEL_PREFIX;
import static org.apache.atlas.repository.graph.GraphHelper.addListProperty;
import static org.apache.atlas.repository.graph.GraphHelper.edgeExists;
import static org.apache.atlas.repository.graph.GraphHelper.getAdjacentEdgesByLabel;
import static org.apache.atlas.repository.graph.GraphHelper.getAllTraitNames;
import static org.apache.atlas.repository.graph.GraphHelper.getAssociatedEntityVertex;
import static org.apache.atlas.repository.graph.GraphHelper.getGuid;
import static org.apache.atlas.repository.graph.GraphHelper.getIncomingEdgesByLabel;
import static org.apache.atlas.repository.graph.GraphHelper.getOutGoingEdgesByLabel;
import static org.apache.atlas.repository.graph.GraphHelper.getPropagateTags;
import static org.apache.atlas.repository.graph.GraphHelper.getRelationshipGuid;
import static org.apache.atlas.repository.graph.GraphHelper.getTypeName;
import static org.apache.atlas.repository.graph.GraphHelper.isPropagationEnabled;
import static org.apache.atlas.repository.graph.GraphHelper.removePropagatedTraitNameFromVertex;
import static org.apache.atlas.repository.store.graph.v1.AtlasGraphUtilsV1.getIdFromVertex;
import static org.apache.atlas.type.AtlasStructType.AtlasAttribute.AtlasRelationshipEdgeDirection;
import static org.apache.atlas.type.AtlasStructType.AtlasAttribute.AtlasRelationshipEdgeDirection.BOTH;
import static org.apache.atlas.type.AtlasStructType.AtlasAttribute.AtlasRelationshipEdgeDirection.IN;
import static org.apache.atlas.type.AtlasStructType.AtlasAttribute.AtlasRelationshipEdgeDirection.OUT;


public final class EntityGraphRetriever {
    private static final Logger LOG = LoggerFactory.getLogger(EntityGraphRetriever.class);

    private final String NAME           = "name";
    private final String DESCRIPTION    = "description";
    private final String OWNER          = "owner";
    private final String CREATE_TIME    = "createTime";
    private final String QUALIFIED_NAME = "qualifiedName";

    private static final List<TimeBoundary> TIME_BOUNDARIES_LIST = new ArrayList<>();
    private static final GraphHelper        graphHelper          = GraphHelper.getInstance();

    private final AtlasTypeRegistry typeRegistry;

    public EntityGraphRetriever(AtlasTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    public AtlasEntity toAtlasEntity(String guid) throws AtlasBaseException {
        return toAtlasEntity(getEntityVertex(guid));
    }

    public AtlasEntity toAtlasEntity(AtlasObjectId objId) throws AtlasBaseException {
        return toAtlasEntity(getEntityVertex(objId));
    }

    public AtlasEntity toAtlasEntity(AtlasVertex entityVertex) throws AtlasBaseException {
        return mapVertexToAtlasEntity(entityVertex, null);
    }

    public AtlasEntityWithExtInfo toAtlasEntityWithExtInfo(String guid) throws AtlasBaseException {
        return toAtlasEntityWithExtInfo(getEntityVertex(guid));
    }

    public AtlasEntityWithExtInfo toAtlasEntityWithExtInfo(AtlasObjectId objId) throws AtlasBaseException {
        return toAtlasEntityWithExtInfo(getEntityVertex(objId));
    }

    public AtlasEntityWithExtInfo toAtlasEntityWithExtInfo(AtlasVertex entityVertex) throws AtlasBaseException {
        AtlasEntityExtInfo     entityExtInfo = new AtlasEntityExtInfo();
        AtlasEntity            entity        = mapVertexToAtlasEntity(entityVertex, entityExtInfo);
        AtlasEntityWithExtInfo ret           = new AtlasEntityWithExtInfo(entity, entityExtInfo);

        ret.compact();

        return ret;
    }

    public AtlasEntitiesWithExtInfo toAtlasEntitiesWithExtInfo(List<String> guids) throws AtlasBaseException {
        AtlasEntitiesWithExtInfo ret = new AtlasEntitiesWithExtInfo();

        for (String guid : guids) {
            AtlasVertex vertex = getEntityVertex(guid);

            AtlasEntity entity = mapVertexToAtlasEntity(vertex, ret);

            ret.addEntity(entity);
        }

        ret.compact();

        return ret;
    }

    public AtlasEntityHeader toAtlasEntityHeader(String guid) throws AtlasBaseException {
        return toAtlasEntityHeader(getEntityVertex(guid));
    }

    public AtlasEntityHeader toAtlasEntityHeader(AtlasVertex entityVertex) throws AtlasBaseException {
        return toAtlasEntityHeader(entityVertex, Collections.<String>emptySet());
    }

    public AtlasEntityHeader toAtlasEntityHeader(AtlasVertex atlasVertex, Set<String> attributes) throws AtlasBaseException {
        return atlasVertex != null ? mapVertexToAtlasEntityHeader(atlasVertex, attributes) : null;
    }

    public AtlasEntityHeader toAtlasEntityHeaderWithClassifications(String guid) throws AtlasBaseException {
        return toAtlasEntityHeaderWithClassifications(getEntityVertex(guid), Collections.emptySet());
    }

    public AtlasEntityHeader toAtlasEntityHeaderWithClassifications(AtlasVertex entityVertex) throws AtlasBaseException {
        return toAtlasEntityHeaderWithClassifications(entityVertex, Collections.emptySet());
    }

    public AtlasEntityHeader toAtlasEntityHeaderWithClassifications(AtlasVertex entityVertex, Set<String> attributes) throws AtlasBaseException {
        AtlasEntityHeader ret = toAtlasEntityHeader(entityVertex, attributes);

        ret.setClassifications(getAllClassifications(entityVertex));

        return ret;
    }

    public AtlasEntityHeader toAtlasEntityHeader(AtlasEntity entity) {
        AtlasEntityHeader ret        = null;
        String            typeName   = entity.getTypeName();
        AtlasEntityType   entityType = typeRegistry.getEntityTypeByName(typeName);

        if (entityType != null) {
            Map<String, Object> uniqueAttributes = new HashMap<>();

            for (AtlasAttribute attribute : entityType.getUniqAttributes().values()) {
                Object attrValue = entity.getAttribute(attribute.getName());

                if (attrValue != null) {
                    uniqueAttributes.put(attribute.getName(), attrValue);
                }
            }

            ret = new AtlasEntityHeader(entity.getTypeName(), entity.getGuid(), uniqueAttributes);

            if (CollectionUtils.isNotEmpty(entity.getClassifications())) {
                List<AtlasClassification> classifications     = new ArrayList<>(entity.getClassifications().size());
                List<String>              classificationNames = new ArrayList<>(entity.getClassifications().size());

                for (AtlasClassification classification : entity.getClassifications()) {
                    classifications.add(classification);
                    classificationNames.add(classification.getTypeName());
                }

                ret.setClassifications(classifications);
                ret.setClassificationNames(classificationNames);
            }
        }

        return ret;
    }

    public AtlasObjectId toAtlasObjectId(AtlasVertex entityVertex) throws AtlasBaseException {
        AtlasObjectId   ret        = null;
        String          typeName   = entityVertex.getProperty(Constants.TYPE_NAME_PROPERTY_KEY, String.class);
        AtlasEntityType entityType = typeRegistry.getEntityTypeByName(typeName);

        if (entityType != null) {
            Map<String, Object> uniqueAttributes = new HashMap<>();

            for (AtlasAttribute attribute : entityType.getUniqAttributes().values()) {
                Object attrValue = getVertexAttribute(entityVertex, attribute);

                if (attrValue != null) {
                    uniqueAttributes.put(attribute.getName(), attrValue);
                }
            }

            ret = new AtlasObjectId(entityVertex.getProperty(Constants.GUID_PROPERTY_KEY, String.class), typeName, uniqueAttributes);
        }

        return ret;
    }

    public AtlasClassification toAtlasClassification(AtlasVertex classificationVertex) throws AtlasBaseException {
        AtlasClassification ret = new AtlasClassification(getTypeName(classificationVertex));

        ret.setEntityGuid(AtlasGraphUtilsV1.getProperty(classificationVertex, CLASSIFICATION_ENTITY_GUID, String.class));
        ret.setPropagate(isPropagationEnabled(classificationVertex));

        String strValidityPeriods = AtlasGraphUtilsV1.getProperty(classificationVertex, CLASSIFICATION_VALIDITY_PERIODS_KEY, String.class);

        if (strValidityPeriods != null) {
            ret.setValidityPeriods(AtlasJson.fromJson(strValidityPeriods, TIME_BOUNDARIES_LIST.getClass()));
        }

        mapAttributes(classificationVertex, ret, null);

        return ret;
    }

    public AtlasVertex getReferencedEntityVertex(AtlasEdge edge, AtlasRelationshipEdgeDirection relationshipDirection, AtlasVertex parentVertex) throws AtlasBaseException {
        AtlasVertex entityVertex = null;

        if (relationshipDirection == OUT) {
            entityVertex = edge.getInVertex();
        } else if (relationshipDirection == IN) {
            entityVertex = edge.getOutVertex();
        } else if (relationshipDirection == BOTH){
            // since relationship direction is BOTH, edge direction can be inward or outward
            // compare with parent entity vertex and pick the right reference vertex
            if (StringUtils.equals(GraphHelper.getGuid(parentVertex), GraphHelper.getGuid(edge.getOutVertex()))) {
                entityVertex = edge.getInVertex();
            } else {
                entityVertex = edge.getOutVertex();
            }
        }

        return entityVertex;
    }

    public AtlasVertex getEntityVertex(String guid) throws AtlasBaseException {
        AtlasVertex ret = AtlasGraphUtilsV1.findByGuid(guid);

        if (ret == null) {
            throw new AtlasBaseException(AtlasErrorCode.INSTANCE_GUID_NOT_FOUND, guid);
        }

        return ret;
    }

    private AtlasVertex getEntityVertex(AtlasObjectId objId) throws AtlasBaseException {
        AtlasVertex ret = null;

        if (! AtlasTypeUtil.isValid(objId)) {
            throw new AtlasBaseException(AtlasErrorCode.INVALID_OBJECT_ID, objId.toString());
        }

        if (AtlasTypeUtil.isAssignedGuid(objId)) {
            ret = AtlasGraphUtilsV1.findByGuid(objId.getGuid());
        } else {
            AtlasEntityType     entityType     = typeRegistry.getEntityTypeByName(objId.getTypeName());
            Map<String, Object> uniqAttributes = objId.getUniqueAttributes();

            ret = AtlasGraphUtilsV1.getVertexByUniqueAttributes(entityType, uniqAttributes);
        }

        if (ret == null) {
            throw new AtlasBaseException(AtlasErrorCode.INSTANCE_GUID_NOT_FOUND, objId.toString());
        }

        return ret;
    }

    private AtlasEntity mapVertexToAtlasEntity(AtlasVertex entityVertex, AtlasEntityExtInfo entityExtInfo) throws AtlasBaseException {
        String      guid   = getGuid(entityVertex);
        AtlasEntity entity = entityExtInfo != null ? entityExtInfo.getEntity(guid) : null;

        if (entity == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Mapping graph vertex to atlas entity for guid {}", guid);
            }

            entity = new AtlasEntity();

            if (entityExtInfo != null) {
                entityExtInfo.addReferredEntity(guid, entity);
            }

            mapSystemAttributes(entityVertex, entity);

            mapAttributes(entityVertex, entity, entityExtInfo);

            mapRelationshipAttributes(entityVertex, entity);

            mapClassifications(entityVertex, entity);
        }

        return entity;
    }

    private AtlasEntityHeader mapVertexToAtlasEntityHeader(AtlasVertex entityVertex) throws AtlasBaseException {
        return mapVertexToAtlasEntityHeader(entityVertex, Collections.<String>emptySet());
    }

    private AtlasEntityHeader mapVertexToAtlasEntityHeader(AtlasVertex entityVertex, Set<String> attributes) throws AtlasBaseException {
        AtlasEntityHeader ret = new AtlasEntityHeader();

        String typeName = entityVertex.getProperty(Constants.TYPE_NAME_PROPERTY_KEY, String.class);
        String guid     = entityVertex.getProperty(Constants.GUID_PROPERTY_KEY, String.class);

        ret.setTypeName(typeName);
        ret.setGuid(guid);
        ret.setStatus(GraphHelper.getStatus(entityVertex));
        ret.setClassificationNames(getAllTraitNames(entityVertex));

        AtlasEntityType entityType = typeRegistry.getEntityTypeByName(typeName);

        if (entityType != null) {
            for (AtlasAttribute uniqueAttribute : entityType.getUniqAttributes().values()) {
                Object attrValue = getVertexAttribute(entityVertex, uniqueAttribute);

                if (attrValue != null) {
                    ret.setAttribute(uniqueAttribute.getName(), attrValue);
                }
            }

            Object name        = getVertexAttribute(entityVertex, entityType.getAttribute(NAME));
            Object description = getVertexAttribute(entityVertex, entityType.getAttribute(DESCRIPTION));
            Object owner       = getVertexAttribute(entityVertex, entityType.getAttribute(OWNER));
            Object createTime  = getVertexAttribute(entityVertex, entityType.getAttribute(CREATE_TIME));
            Object displayText = name != null ? name : ret.getAttribute(QUALIFIED_NAME);

            ret.setAttribute(NAME, name);
            ret.setAttribute(DESCRIPTION, description);
            ret.setAttribute(OWNER, owner);
            ret.setAttribute(CREATE_TIME, createTime);

            if (displayText != null) {
                ret.setDisplayText(displayText.toString());
            }

            if (CollectionUtils.isNotEmpty(attributes)) {
                for (String attrName : attributes) {
                    String nonQualifiedAttrName = toNonQualifiedName(attrName);
                    if (ret.hasAttribute(attrName)) {
                        continue;
                    }

                    Object attrValue = getVertexAttribute(entityVertex, entityType.getAttribute(nonQualifiedAttrName));

                    if (attrValue != null) {
                        ret.setAttribute(nonQualifiedAttrName, attrValue);
                    }
                }
            }
        }

        return ret;
    }

    private String toNonQualifiedName(String attrName) {
        String ret;
        if (attrName.contains(".")) {
            String[] attributeParts = attrName.split("\\.");
            ret = attributeParts[attributeParts.length - 1];
        } else {
            ret = attrName;
        }
        return ret;
    }

    private AtlasEntity mapSystemAttributes(AtlasVertex entityVertex, AtlasEntity entity) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping system attributes for type {}", entity.getTypeName());
        }

        entity.setGuid(getGuid(entityVertex));
        entity.setTypeName(getTypeName(entityVertex));
        entity.setStatus(GraphHelper.getStatus(entityVertex));
        entity.setVersion(GraphHelper.getVersion(entityVertex));

        entity.setCreatedBy(GraphHelper.getCreatedByAsString(entityVertex));
        entity.setUpdatedBy(GraphHelper.getModifiedByAsString(entityVertex));

        entity.setCreateTime(new Date(GraphHelper.getCreatedTime(entityVertex)));
        entity.setUpdateTime(new Date(GraphHelper.getModifiedTime(entityVertex)));

        return entity;
    }

    private void mapAttributes(AtlasVertex entityVertex, AtlasStruct struct, AtlasEntityExtInfo entityExtInfo) throws AtlasBaseException {
        AtlasType objType = typeRegistry.getType(struct.getTypeName());

        if (!(objType instanceof AtlasStructType)) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_NAME_INVALID, struct.getTypeName());
        }

        AtlasStructType structType = (AtlasStructType) objType;

        for (AtlasAttribute attribute : structType.getAllAttributes().values()) {
            Object attrValue = mapVertexToAttribute(entityVertex, attribute, entityExtInfo);

            struct.setAttribute(attribute.getName(), attrValue);
        }
    }

    public List<AtlasClassification> getAllClassifications(AtlasVertex entityVertex) throws AtlasBaseException {
        List<AtlasClassification> ret   = new ArrayList<>();
        Iterable                  edges = entityVertex.query().direction(AtlasEdgeDirection.OUT).label(CLASSIFICATION_LABEL).edges();

        if (edges != null) {
            Iterator<AtlasEdge> iterator = edges.iterator();

            while (iterator.hasNext()) {
                AtlasEdge edge = iterator.next();

                if (edge != null) {
                    ret.add(toAtlasClassification(edge.getInVertex()));
                }
            }
        }

        return ret;
    }

    protected List<AtlasVertex> getPropagationEnabledClassificationVertices(AtlasVertex entityVertex) {
        List<AtlasVertex> ret   = new ArrayList<>();
        Iterable          edges = entityVertex.query().direction(AtlasEdgeDirection.OUT).label(CLASSIFICATION_LABEL).edges();

        if (edges != null) {
            Iterator<AtlasEdge> iterator = edges.iterator();

            while (iterator.hasNext()) {
                AtlasEdge edge = iterator.next();

                if (edge != null) {
                    AtlasVertex classificationVertex = edge.getInVertex();

                    if (isPropagationEnabled(classificationVertex)) {
                        ret.add(classificationVertex);
                    }
                }
            }
        }

        return ret;
    }

    private void mapClassifications(AtlasVertex entityVertex, AtlasEntity entity) throws AtlasBaseException {
        final List<AtlasClassification> classifications = getAllClassifications(entityVertex);

        entity.setClassifications(classifications);
    }

    private Object mapVertexToAttribute(AtlasVertex entityVertex, AtlasAttribute attribute, AtlasEntityExtInfo entityExtInfo) throws AtlasBaseException {
        Object    ret                = null;
        AtlasType attrType           = attribute.getAttributeType();
        String    vertexPropertyName = attribute.getQualifiedName();
        String    edgeLabel          = EDGE_LABEL_PREFIX + vertexPropertyName;
        boolean   isOwnedAttribute   = attribute.isOwnedRef();
        AtlasRelationshipEdgeDirection edgeDirection = attribute.getRelationshipEdgeDirection();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping vertex {} to atlas entity {}.{}", entityVertex, attribute.getDefinedInDef().getName(), attribute.getName());
        }

        switch (attrType.getTypeCategory()) {
            case PRIMITIVE:
                ret = mapVertexToPrimitive(entityVertex, vertexPropertyName, attribute.getAttributeDef());
                break;
            case ENUM:
                ret = GraphHelper.getProperty(entityVertex, vertexPropertyName);
                break;
            case STRUCT:
                ret = mapVertexToStruct(entityVertex, edgeLabel, null, entityExtInfo);
                break;
            case OBJECT_ID_TYPE:
                ret = mapVertexToObjectId(entityVertex, edgeLabel, null, entityExtInfo, isOwnedAttribute, edgeDirection);
                break;
            case ARRAY:
                ret = mapVertexToArray(entityVertex, (AtlasArrayType) attrType, vertexPropertyName, entityExtInfo, isOwnedAttribute, edgeDirection);
                break;
            case MAP:
                ret = mapVertexToMap(entityVertex, (AtlasMapType) attrType, vertexPropertyName, entityExtInfo, isOwnedAttribute, edgeDirection);
                break;
            case CLASSIFICATION:
                // do nothing
                break;
        }

        return ret;
    }

    private Map<String, Object> mapVertexToMap(AtlasVertex entityVertex, AtlasMapType atlasMapType, final String propertyName,
                                               AtlasEntityExtInfo entityExtInfo, boolean isOwnedAttribute,
                                               AtlasRelationshipEdgeDirection edgeDirection) throws AtlasBaseException {

        List<String> mapKeys = GraphHelper.getListProperty(entityVertex, propertyName);

        if (CollectionUtils.isEmpty(mapKeys)) {
            return null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping map attribute {} for vertex {}", atlasMapType.getTypeName(), entityVertex);
        }

        Map<String, Object> ret          = new HashMap<>(mapKeys.size());
        AtlasType           mapValueType = atlasMapType.getValueType();

        for (String mapKey : mapKeys) {
            final String keyPropertyName = propertyName + "." + mapKey;
            final String edgeLabel       = EDGE_LABEL_PREFIX + keyPropertyName;
            final Object keyValue        = GraphHelper.getMapValueProperty(mapValueType, entityVertex, keyPropertyName);

            Object mapValue = mapVertexToCollectionEntry(entityVertex, mapValueType, keyValue, edgeLabel,
                                                         entityExtInfo, isOwnedAttribute, edgeDirection);

            if (mapValue != null) {
                ret.put(mapKey, mapValue);
            }
        }

        return ret;
    }

    private List<Object> mapVertexToArray(AtlasVertex entityVertex, AtlasArrayType arrayType, String propertyName,
                                          AtlasEntityExtInfo entityExtInfo, boolean isOwnedAttribute,
                                          AtlasRelationshipEdgeDirection edgeDirection)  throws AtlasBaseException {

        AtlasType    arrayElementType = arrayType.getElementType();
        List<Object> arrayElements    = GraphHelper.getArrayElementsProperty(arrayElementType, entityVertex, propertyName);

        if (CollectionUtils.isEmpty(arrayElements)) {
            return null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping array attribute {} for vertex {}", arrayElementType.getTypeName(), entityVertex);
        }

        List   arrValues = new ArrayList(arrayElements.size());
        String edgeLabel = EDGE_LABEL_PREFIX + propertyName;

        for (Object element : arrayElements) {
            Object arrValue = mapVertexToCollectionEntry(entityVertex, arrayElementType, element, edgeLabel,
                                                         entityExtInfo, isOwnedAttribute, edgeDirection);

            if (arrValue != null) {
                arrValues.add(arrValue);
            }
        }

        return arrValues;
    }

    private Object mapVertexToCollectionEntry(AtlasVertex entityVertex, AtlasType arrayElement, Object value,
                                              String edgeLabel, AtlasEntityExtInfo entityExtInfo, boolean isOwnedAttribute,
                                              AtlasRelationshipEdgeDirection edgeDirection) throws AtlasBaseException {
        Object ret = null;

        switch (arrayElement.getTypeCategory()) {
            case PRIMITIVE:
            case ENUM:
            case ARRAY:
            case MAP:
                ret = value;
                break;

            case CLASSIFICATION:
                break;

            case STRUCT:
                ret = mapVertexToStruct(entityVertex, edgeLabel, (AtlasEdge) value, entityExtInfo);
                break;

            case OBJECT_ID_TYPE:
                ret = mapVertexToObjectId(entityVertex, edgeLabel, (AtlasEdge) value, entityExtInfo, isOwnedAttribute, edgeDirection);
                break;

            default:
                break;
        }

        return ret;
    }

    public Object mapVertexToPrimitive(AtlasElement entityVertex, final String vertexPropertyName, AtlasAttributeDef attrDef) {
        Object ret = null;

        if (GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Object.class) == null) {
            return null;
        }

        switch (attrDef.getTypeName().toLowerCase()) {
            case ATLAS_TYPE_STRING:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, String.class);
                break;
            case ATLAS_TYPE_SHORT:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Short.class);
                break;
            case ATLAS_TYPE_INT:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Integer.class);
                break;
            case ATLAS_TYPE_BIGINTEGER:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, BigInteger.class);
                break;
            case ATLAS_TYPE_BOOLEAN:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Boolean.class);
                break;
            case ATLAS_TYPE_BYTE:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Byte.class);
                break;
            case ATLAS_TYPE_LONG:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Long.class);
                break;
            case ATLAS_TYPE_FLOAT:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Float.class);
                break;
            case ATLAS_TYPE_DOUBLE:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Double.class);
                break;
            case ATLAS_TYPE_BIGDECIMAL:
                ret = GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, BigDecimal.class);
                break;
            case ATLAS_TYPE_DATE:
                ret = new Date(GraphHelper.getSingleValuedProperty(entityVertex, vertexPropertyName, Long.class));
                break;
            default:
                break;
        }

        return ret;
    }

    private AtlasObjectId mapVertexToObjectId(AtlasVertex entityVertex, String edgeLabel, AtlasEdge edge,
                                              AtlasEntityExtInfo entityExtInfo, boolean isOwnedAttribute,
                                              AtlasRelationshipEdgeDirection edgeDirection) throws AtlasBaseException {
        AtlasObjectId ret = null;

        if (edge == null) {
            edge = graphHelper.getEdgeForLabel(entityVertex, edgeLabel, edgeDirection);
        }

        if (GraphHelper.elementExists(edge)) {
            AtlasVertex referenceVertex = edge.getInVertex();

            if (StringUtils.equals(getIdFromVertex(referenceVertex), getIdFromVertex(entityVertex))) {
                referenceVertex = edge.getOutVertex();
            }

            if (referenceVertex != null) {
                if (entityExtInfo != null && isOwnedAttribute) {
                    AtlasEntity entity = mapVertexToAtlasEntity(referenceVertex, entityExtInfo);

                    if (entity != null) {
                        ret = AtlasTypeUtil.getAtlasObjectId(entity);
                    }
                } else {
                    ret = new AtlasObjectId(getGuid(referenceVertex), getTypeName(referenceVertex));
                }
            }
        }

        return ret;
    }

    private AtlasStruct mapVertexToStruct(AtlasVertex entityVertex, String edgeLabel, AtlasEdge edge, AtlasEntityExtInfo entityExtInfo) throws AtlasBaseException {
        AtlasStruct ret = null;

        if (edge == null) {
            edge = graphHelper.getEdgeForLabel(entityVertex, edgeLabel);
        }

        if (GraphHelper.elementExists(edge)) {
            final AtlasVertex referenceVertex = edge.getInVertex();
            ret = new AtlasStruct(getTypeName(referenceVertex));

            mapAttributes(referenceVertex, ret, entityExtInfo);
        }

        return ret;
    }

    private Object getVertexAttribute(AtlasVertex vertex, AtlasAttribute attribute) throws AtlasBaseException {
        return vertex != null && attribute != null ? mapVertexToAttribute(vertex, attribute, null) : null;
    }

    private void mapRelationshipAttributes(AtlasVertex entityVertex, AtlasEntity entity) throws AtlasBaseException {
        AtlasEntityType entityType = typeRegistry.getEntityTypeByName(entity.getTypeName());

        if (entityType == null) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_NAME_INVALID, entity.getTypeName());
        }

        for (AtlasAttribute attribute : entityType.getRelationshipAttributes().values()) {
            Object attrValue = mapVertexToRelationshipAttribute(entityVertex, entityType, attribute);

            entity.setRelationshipAttribute(attribute.getName(), attrValue);
        }
    }

    private Object mapVertexToRelationshipAttribute(AtlasVertex entityVertex, AtlasEntityType entityType, AtlasAttribute attribute) throws AtlasBaseException {
        Object               ret             = null;
        AtlasRelationshipDef relationshipDef = graphHelper.getRelationshipDef(entityVertex, entityType, attribute.getName());

        if (relationshipDef == null) {
            throw new AtlasBaseException(AtlasErrorCode.RELATIONSHIPDEF_INVALID, "relationshipDef is null");
        }

        AtlasRelationshipEndDef endDef1         = relationshipDef.getEndDef1();
        AtlasRelationshipEndDef endDef2         = relationshipDef.getEndDef2();
        AtlasEntityType         endDef1Type     = typeRegistry.getEntityTypeByName(endDef1.getType());
        AtlasEntityType         endDef2Type     = typeRegistry.getEntityTypeByName(endDef2.getType());
        AtlasRelationshipEndDef attributeEndDef = null;

        if (endDef1Type.isTypeOrSuperTypeOf(entityType.getTypeName()) && StringUtils.equals(endDef1.getName(), attribute.getName())) {
            attributeEndDef = endDef1;
        } else if (endDef2Type.isTypeOrSuperTypeOf(entityType.getTypeName()) && StringUtils.equals(endDef2.getName(), attribute.getName())) {
            attributeEndDef = endDef2;
        }

        if (attributeEndDef == null) {
            throw new AtlasBaseException(AtlasErrorCode.RELATIONSHIPDEF_INVALID, relationshipDef.toString());
        }

        switch (attributeEndDef.getCardinality()) {
            case SINGLE:
                ret = mapRelatedVertexToObjectId(entityVertex, attribute);
                break;

            case LIST:
            case SET:
                ret = mapRelationshipArrayAttribute(entityVertex, attribute);
                break;
        }

        return ret;
    }

    private AtlasObjectId mapRelatedVertexToObjectId(AtlasVertex entityVertex, AtlasAttribute attribute) throws AtlasBaseException {
        AtlasEdge edge = graphHelper.getEdgeForLabel(entityVertex, attribute.getRelationshipEdgeLabel(), attribute.getRelationshipEdgeDirection());

        return mapVertexToRelatedObjectId(entityVertex, edge);
    }

    private List<AtlasRelatedObjectId> mapRelationshipArrayAttribute(AtlasVertex entityVertex, AtlasAttribute attribute) throws AtlasBaseException {
        List<AtlasRelatedObjectId> ret   = new ArrayList<>();
        Iterator<AtlasEdge>        edges = null;

        if (attribute.getRelationshipEdgeDirection() == IN) {
            edges = getIncomingEdgesByLabel(entityVertex, attribute.getRelationshipEdgeLabel());
        } else if (attribute.getRelationshipEdgeDirection() == OUT) {
            edges = getOutGoingEdgesByLabel(entityVertex, attribute.getRelationshipEdgeLabel());
        } else if (attribute.getRelationshipEdgeDirection() == BOTH) {
            edges = getAdjacentEdgesByLabel(entityVertex, AtlasEdgeDirection.BOTH, attribute.getRelationshipEdgeLabel());
        }

        if (edges != null) {
            while (edges.hasNext()) {
                AtlasEdge relationshipEdge = edges.next();

                AtlasRelatedObjectId relatedObjectId = mapVertexToRelatedObjectId(entityVertex, relationshipEdge);

                ret.add(relatedObjectId);
            }
        }

        return ret;
    }

    private AtlasRelatedObjectId mapVertexToRelatedObjectId(AtlasVertex entityVertex, AtlasEdge edge) throws AtlasBaseException {
        AtlasRelatedObjectId ret = null;

        if (GraphHelper.elementExists(edge)) {
            AtlasVertex referenceVertex = edge.getInVertex();

            if (StringUtils.equals(getIdFromVertex(referenceVertex), getIdFromVertex(entityVertex))) {
                referenceVertex = edge.getOutVertex();
            }

            if (referenceVertex != null) {
                String            entityTypeName = getTypeName(referenceVertex);
                String            entityGuid     = getGuid(referenceVertex);
                AtlasRelationship relationship   = mapEdgeToAtlasRelationship(edge);

                ret = new AtlasRelatedObjectId(entityGuid, entityTypeName, relationship.getGuid(),
                                               new AtlasStruct(relationship.getTypeName(), relationship.getAttributes()));

                Object displayText = getDisplayText(referenceVertex, entityTypeName);

                if (displayText != null) {
                    ret.setDisplayText(displayText.toString());
                }
            }
        }

        return ret;
    }

    private Object getDisplayText(AtlasVertex entityVertex, String entityTypeName) throws AtlasBaseException {
        AtlasEntityType entityType = typeRegistry.getEntityTypeByName(entityTypeName);
        Object          ret        = null;

        if (entityType != null) {
            ret = getVertexAttribute(entityVertex, entityType.getAttribute(NAME));

            if (ret == null) {
                ret = getVertexAttribute(entityVertex, entityType.getAttribute(QUALIFIED_NAME));
            }
        }

        return ret;
    }

    public AtlasRelationship mapEdgeToAtlasRelationship(AtlasEdge edge) throws AtlasBaseException {
        AtlasRelationship ret = new AtlasRelationship();

        mapSystemAttributes(edge, ret);

        mapAttributes(edge, ret);

        return ret;
    }

    private AtlasRelationship mapSystemAttributes(AtlasEdge edge, AtlasRelationship relationship) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping system attributes for relationship");
        }

        relationship.setGuid(getRelationshipGuid(edge));
        relationship.setTypeName(getTypeName(edge));

        relationship.setCreatedBy(GraphHelper.getCreatedByAsString(edge));
        relationship.setUpdatedBy(GraphHelper.getModifiedByAsString(edge));

        relationship.setCreateTime(new Date(GraphHelper.getCreatedTime(edge)));
        relationship.setUpdateTime(new Date(GraphHelper.getModifiedTime(edge)));

        Long version = GraphHelper.getVersion(edge);

        if (version == null) {
            version = Long.valueOf(1L);
        }

        relationship.setVersion(version);
        relationship.setStatus(GraphHelper.getEdgeStatus(edge));

        AtlasVertex end1Vertex = edge.getOutVertex();
        AtlasVertex end2Vertex = edge.getInVertex();

        relationship.setEnd1(new AtlasObjectId(getGuid(end1Vertex), getTypeName(end1Vertex)));
        relationship.setEnd2(new AtlasObjectId(getGuid(end2Vertex), getTypeName(end2Vertex)));

        relationship.setLabel(edge.getLabel());
        relationship.setPropagateTags(getPropagateTags(edge));

        return relationship;
    }

    private void mapAttributes(AtlasEdge edge, AtlasRelationship relationship) throws AtlasBaseException {
        AtlasType objType = typeRegistry.getType(relationship.getTypeName());

        if (!(objType instanceof AtlasRelationshipType)) {
            throw new AtlasBaseException(AtlasErrorCode.TYPE_NAME_INVALID, relationship.getTypeName());
        }

        AtlasRelationshipType relationshipType = (AtlasRelationshipType) objType;

        for (AtlasAttribute attribute : relationshipType.getAllAttributes().values()) {
            // mapping only primitive attributes
            Object attrValue = mapVertexToPrimitive(edge, attribute.getQualifiedName(), attribute.getAttributeDef());

            relationship.setAttribute(attribute.getName(), attrValue);
        }
    }

    public void addTagPropagation(AtlasEdge edge, PropagateTags propagateTags) throws AtlasBaseException {
        if (edge == null) {
            return;
        }

        AtlasVertex outVertex = edge.getOutVertex();
        AtlasVertex inVertex  = edge.getInVertex();

        if (propagateTags == PropagateTags.ONE_TO_TWO || propagateTags == PropagateTags.BOTH) {
            addTagPropagation(outVertex, inVertex, edge);
        }

        if (propagateTags == PropagateTags.TWO_TO_ONE || propagateTags == PropagateTags.BOTH) {
            addTagPropagation(inVertex, outVertex, edge);
        }
    }

    public void removeTagPropagation(AtlasEdge edge, PropagateTags propagateTags) throws AtlasBaseException {
        if (edge == null) {
            return;
        }

        AtlasVertex outVertex = edge.getOutVertex();
        AtlasVertex inVertex  = edge.getInVertex();

        if (propagateTags == PropagateTags.ONE_TO_TWO || propagateTags == PropagateTags.BOTH) {
            removeTagPropagation(outVertex, inVertex, edge);
        }

        if (propagateTags == PropagateTags.TWO_TO_ONE || propagateTags == PropagateTags.BOTH) {
            removeTagPropagation(inVertex, outVertex, edge);
        }
    }

    private void addTagPropagation(AtlasVertex fromVertex, AtlasVertex toVertex, AtlasEdge edge) throws AtlasBaseException {
        final List<AtlasVertex> classificationVertices = getPropagationEnabledClassificationVertices(fromVertex);
        final List<AtlasVertex> impactedEntityVertices = CollectionUtils.isNotEmpty(classificationVertices) ? graphHelper.getIncludedImpactedVerticesWithReferences(toVertex, getRelationshipGuid(edge)) : null;

        if (CollectionUtils.isNotEmpty(impactedEntityVertices)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Propagate {} tags: from {} entity to {} entities", classificationVertices.size(), getTypeName(fromVertex), impactedEntityVertices.size());
            }

            for (AtlasVertex classificationVertex : classificationVertices) {
                String                  classificationName     = getTypeName(classificationVertex);
                AtlasVertex             associatedEntityVertex = getAssociatedEntityVertex(classificationVertex);
                AtlasClassificationType classificationType     = typeRegistry.getClassificationTypeByName(classificationName);

                for (AtlasVertex impactedEntityVertex : impactedEntityVertices) {
                    if (edgeExists(impactedEntityVertex, classificationVertex, classificationName)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(" --> Classification edge already exists from [{}] --> [{}][{}] using edge label: [{}]",
                                    getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), classificationName);
                        }

                        continue;
                    } else if (edgeExists(impactedEntityVertex, classificationVertex, CLASSIFICATION_LABEL)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(" --> Propagated classification edge already exists from [{}] --> [{}][{}] using edge label: [{}]",
                                    getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), CLASSIFICATION_LABEL);
                        }

                        continue;
                    }

                    String          entityTypeName = getTypeName(impactedEntityVertex);
                    AtlasEntityType entityType     = typeRegistry.getEntityTypeByName(entityTypeName);

                    if (!classificationType.canApplyToEntityType(entityType)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(" --> Not creating propagated classification edge from [{}] --> [{}][{}], classification is not applicable for entity type",
                                        getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex));
                        }

                        continue;
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" --> Creating propagated classification edge from [{}] --> [{}][{}] using edge label: [{}]",
                                  getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), CLASSIFICATION_LABEL);
                    }

                    graphHelper.addClassificationEdge(impactedEntityVertex, classificationVertex, true);

                    addListProperty(impactedEntityVertex, PROPAGATED_TRAIT_NAMES_PROPERTY_KEY, classificationName);
                }
            }
        }
    }

    private void removeTagPropagation(AtlasVertex fromVertex, AtlasVertex toVertex, AtlasEdge edge) throws AtlasBaseException {
        final List<AtlasVertex> classificationVertices = getPropagationEnabledClassificationVertices(fromVertex);
        final List<AtlasVertex> impactedEntityVertices = CollectionUtils.isNotEmpty(classificationVertices) ? graphHelper.getIncludedImpactedVerticesWithReferences(toVertex, getRelationshipGuid(edge)) : null;

        if (CollectionUtils.isNotEmpty(impactedEntityVertices)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Removing {} propagated tags: for {} from {} entities", classificationVertices.size(), getTypeName(fromVertex), impactedEntityVertices.size());
            }

            for (AtlasVertex classificationVertex : classificationVertices) {
                String            classificationName     = getTypeName(classificationVertex);
                AtlasVertex       associatedEntityVertex = getAssociatedEntityVertex(classificationVertex);
                List<AtlasVertex> referrals              = graphHelper.getIncludedImpactedVerticesWithReferences(associatedEntityVertex, getRelationshipGuid(edge));

                for (AtlasVertex impactedEntityVertex : impactedEntityVertices) {
                    if (referrals.contains(impactedEntityVertex)) {
                        if (LOG.isDebugEnabled()) {
                            if (StringUtils.equals(getGuid(impactedEntityVertex), getGuid(associatedEntityVertex))) {
                                LOG.debug(" --> Not removing propagated classification edge from [{}] --> [{}][{}] with edge label: [{}], since [{}] is associated with [{}]",
                                          getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), CLASSIFICATION_LABEL, classificationName, getTypeName(associatedEntityVertex));
                            } else {
                                LOG.debug(" --> Not removing propagated classification edge from [{}] --> [{}][{}] with edge label: [{}], since [{}] is propagated through other path",
                                          getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), CLASSIFICATION_LABEL, classificationName);
                            }
                        }

                        continue;
                    }

                    // remove propagated classification edge and classificationName from propagatedTraitNames vertex property
                    if (edgeExists(impactedEntityVertex, classificationVertex, CLASSIFICATION_LABEL)) {
                        try {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug(" --> Removing propagated classification edge from [{}] --> [{}][{}] with edge label: [{}]",
                                          getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), CLASSIFICATION_LABEL);
                            }

                            AtlasEdge propagatedEdge = graphHelper.getOrCreateEdge(impactedEntityVertex, classificationVertex, CLASSIFICATION_LABEL);

                            graphHelper.removeEdge(propagatedEdge);

                            removePropagatedTraitNameFromVertex(impactedEntityVertex, classificationName);
                        } catch (RepositoryException e) {
                            throw new AtlasBaseException(AtlasErrorCode.INTERNAL_ERROR, e);
                        }
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(" --> Not removing propagated classification edge from [{}] --> [{}][{}] using edge label: [{}], since edge doesn't exist",
                                      getTypeName(impactedEntityVertex), getTypeName(classificationVertex), getTypeName(associatedEntityVertex), CLASSIFICATION_LABEL);
                        }
                    }
                }
            }
        }
    }
}