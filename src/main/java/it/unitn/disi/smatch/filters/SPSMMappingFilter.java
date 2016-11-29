package it.unitn.disi.smatch.filters;

import it.unitn.disi.smatch.async.AsyncTask;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.data.mappings.IMappingFactory;
import it.unitn.disi.smatch.data.mappings.MappingElement;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.matchers.structure.tree.spsm.ted.TreeEditDistance;
import it.unitn.disi.smatch.matchers.structure.tree.spsm.ted.utils.impl.MatchedTreeNodeComparator;
import it.unitn.disi.smatch.matchers.structure.tree.spsm.ted.utils.impl.WorstCaseDistanceConversion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Filters the set of mapping elements to preserve a set of structural properties,
 * namely:
 * <ul>
 * <li> one-to-one correspondences between semantically related nodes (this is required
 * in order to match only one parameter (or function) in the first input tree,
 * to only one parameter (or function) in the second input tree);
 * <li> leaf nodes are matched to leaf nodes and internal nodes are matched to internal nodes (the
 * rationale behind this property is that a leaf node represents a parameter of a function,
 * and an internal node corresponds to a function. This way a parameter which contains a
 * value will not be confused with a function).
 * </ul>
 * <p/>
 * It also computes the similarity score between the two contexts being matched.
 * For further details refer to:
 * Approximate structure-preserving semantic matching
 * by
 * Giunchiglia, Fausto and McNeill, Fiona and Yatskevich, Mikalai and
 * Pane, Juan and Besana, Paolo and Shvaiko, Pavel (2008)
 * <a href="http://eprints.biblio.unitn.it/archive/00001459/">http://eprints.biblio.unitn.it/archive/00001459/</a>
 *
 * @author Juan Pane pane@disi.unitn.it
 */
public class SPSMMappingFilter extends BaseFilter implements IMappingFilter, IAsyncMappingFilter {

    private static final Logger log = LoggerFactory.getLogger(SPSMMappingFilter.class);

    public SPSMMappingFilter(IMappingFactory mappingFactory) {
        super(mappingFactory);
    }

    public SPSMMappingFilter(IMappingFactory mappingFactory, IContextMapping<INode> mapping) {
        super(mappingFactory, mapping);
    }

    /**
     * Sorts the siblings in the source and target tree defined in the constructor using
     * the given mapping.
     */
    protected IContextMapping<INode> process(IContextMapping<INode> mapping) throws MappingFilterException {
        //add the first mapping element for the root to the mappings result
        if (0 < mapping.size()) {

            IContext sourceContext = mapping.getSourceContext();
            IContext targetContext = mapping.getTargetContext();

            //used for reordering of siblings
            List<Integer> sourceIndex = new ArrayList<>();
            List<Integer> targetIndex = new ArrayList<>();

            //the mapping to be returned by the filter
            IContextMapping<INode> spsmMapping = mappingFactory.getContextMappingInstance(sourceContext, targetContext);

            spsmMapping.setSimilarity(computeSimilarity(mapping));
            log.info("Similarity: " + spsmMapping.getSimilarity());

            if (isRelated(sourceContext.getRoot(), targetContext.getRoot(), IMappingElement.EQUIVALENCE, mapping) ||
                    isRelated(sourceContext.getRoot(), targetContext.getRoot(), IMappingElement.LESS_GENERAL, mapping) ||
                    isRelated(sourceContext.getRoot(), targetContext.getRoot(), IMappingElement.MORE_GENERAL, mapping)) {

                setStrongestMapping(sourceContext.getRoot(), targetContext.getRoot(), mapping, spsmMapping);
                filterMappingsOfChildren(sourceContext.getRoot(), targetContext.getRoot(), IMappingElement.EQUIVALENCE,
                        sourceIndex, targetIndex, mapping, spsmMapping);
                filterMappingsOfChildren(sourceContext.getRoot(), targetContext.getRoot(), IMappingElement.MORE_GENERAL,
                        sourceIndex, targetIndex, mapping, spsmMapping);
                filterMappingsOfChildren(sourceContext.getRoot(), targetContext.getRoot(), IMappingElement.LESS_GENERAL,
                        sourceIndex, targetIndex, mapping, spsmMapping);
            }

            return spsmMapping;
        }

        return mapping;
    }

    @Override
    public AsyncTask<IContextMapping<INode>, IMappingElement<INode>> asyncFilter(IContextMapping<INode> mapping) {
        return new SPSMMappingFilter(mappingFactory, mapping);
    }

    /**
     * Computes the similarity score according to the definition provided in
     * Approximate structure-preserving semantic matching.
     * By
     * Giunchiglia, Fausto and McNeill, Fiona and Yatskevich, Mikalai and
     * Pane, Juan and Besana, Paolo and Shvaiko, Pavel (2008)
     * <a href="http://eprints.biblio.unitn.it/archive/00001459/">http://eprints.biblio.unitn.it/archive/00001459/</a>.
     *
     * @param mapping mapping
     * @return similarity score
     */
    protected double computeSimilarity(IContextMapping<INode> mapping) {
        MatchedTreeNodeComparator mntc = new MatchedTreeNodeComparator(mapping);
        TreeEditDistance tde = new TreeEditDistance(mapping.getSourceContext(), mapping.getTargetContext(), mntc, new WorstCaseDistanceConversion());

        tde.calculate();
        double ed = tde.getTreeEditDistance();

        return 1 - (ed / Math.max(mapping.getSourceContext().nodesCount(), mapping.getTargetContext().nodesCount()));
    }


    /**
     * Sorts the children of the given nodes.
     *
     * @param sourceParent     Source node.
     * @param targetParent     Target node.
     * @param semanticRelation the relation to use for comparison.
     * @param sourceIndex      list used for reordering of siblings
     * @param targetIndex      list used for reordering of siblings
     */
    protected void filterMappingsOfChildren(INode sourceParent, INode targetParent, char semanticRelation,
                                          List<Integer> sourceIndex, List<Integer> targetIndex,
                                          IContextMapping<INode> mapping,
                                          IContextMapping<INode> spsmMapping) {
        List<INode> source = new ArrayList<>(sourceParent.getChildren());
        List<INode> target = new ArrayList<>(targetParent.getChildren());

        sourceIndex.add(sourceParent.ancestorCount(), 0);
        targetIndex.add(targetParent.ancestorCount(), 0);

        if (source.size() >= 1 && target.size() >= 1) {
            //sorts the siblings first with the strongest relation, and then with the others
            filterMappingsOfSiblingsByRelation(source, target, semanticRelation,
                    sourceIndex, targetIndex,
                    mapping, spsmMapping);
        }

        sourceIndex.remove(sourceParent.ancestorCount());
        targetIndex.remove(targetParent.ancestorCount());
    }


    /**
     * Filters the mappings of two siblings node list for which the parents are also supposed to
     * be related. Checks whether in the two given node list there is a pair of nodes related
     * by the given relation and if so, if deletes all the other relations for the given 2
     * nodes setting the current one as strongest.
     *
     * @param source           Source list of siblings.
     * @param target           Target list of siblings.
     * @param semanticRelation a char representing the semantic relation as defined in IMappingElement.
     * @param sourceIndex      list used for reordering of siblings
     * @param targetIndex      list used for reordering of siblings
     * @param mapping          original mapping
     */
    protected void filterMappingsOfSiblingsByRelation(List<INode> source, List<INode> target, char semanticRelation,
                                                    List<Integer> sourceIndex, List<Integer> targetIndex,
                                                    IContextMapping<INode> mapping,
                                                    IContextMapping<INode> spsmMapping) {
        int sourceDepth = (source.get(0).ancestorCount() - 1);
        int targetDepth = (target.get(0).ancestorCount() - 1);

        int sourceSize = source.size();
        int targetSize = target.size();

        while (sourceIndex.get(sourceDepth) < sourceSize && targetIndex.get(targetDepth) < targetSize) {
            if (isRelated(source.get(sourceIndex.get(sourceDepth)),
                    target.get(targetIndex.get(targetDepth)),
                    semanticRelation, mapping)) {

                //sort the children of the matched node
                setStrongestMapping(source.get(sourceIndex.get(sourceDepth)),
                        target.get(targetIndex.get(targetDepth)),
                        mapping,
                        spsmMapping);
                filterMappingsOfChildren(source.get(sourceIndex.get(sourceDepth)),
                        target.get(targetIndex.get(targetDepth)),
                        semanticRelation, sourceIndex, targetIndex, mapping, spsmMapping);

                //increment the index
                inc(sourceIndex, sourceDepth);
                inc(targetIndex, targetDepth);
            } else {
                //look for the next related node in the target
                int relatedIndex = getRelatedIndex(source, target, semanticRelation,
                        sourceIndex, targetIndex, mapping, spsmMapping);
                if (relatedIndex > sourceIndex.get(sourceDepth)) {
                    //there is a related node, but further between the siblings
                    //they should be swapped
                    swapINodes(target, targetIndex.get(targetDepth), relatedIndex);

                    //filter the mappings of the children of the matched node
                    filterMappingsOfChildren(source.get(sourceIndex.get(sourceDepth)),
                            target.get(targetIndex.get(targetDepth)),
                            semanticRelation, sourceIndex, targetIndex, mapping, spsmMapping);

                    //increment the index
                    inc(sourceIndex, sourceDepth);
                    inc(targetIndex, targetDepth);

                } else {
                    //there is not related item among the remaining siblings
                    //swap this element of source with the last, and decrement the sourceSize
                    swapINodes(source, sourceIndex.get(sourceDepth), (sourceSize - 1));

                    sourceSize--;
                }
            }
        }
    }


    /**
     * Swaps the INodes in listOfNodes in the positions source and target.
     *
     * @param listOfNodes List of INodes of which the elements should be swapped.
     * @param source      index of the source element to be swapped.
     * @param target      index of the target element to be swapped.
     */
    protected void swapINodes(List<INode> listOfNodes, int source, int target) {
        INode aux = listOfNodes.get(source);
        listOfNodes.set(source, listOfNodes.get(target));
        listOfNodes.set(target, aux);
    }


    /**
     * Looks for the related index for the source list at the position sourceIndex
     * in the target list beginning at the targetIndex position for the defined relation.
     *
     * @param source      source list of siblings.
     * @param target      target list of siblings.
     * @param relation    relation
     * @param sourceIndex list used for reordering of siblings
     * @param targetIndex list used for reordering of siblings
     * @return the index of the related element in target, or -1 if there is no relate element.
     */
    protected int getRelatedIndex(List<INode> source, List<INode> target, char relation,
                                List<Integer> sourceIndex, List<Integer> targetIndex,
                                IContextMapping<INode> mapping,
                                IContextMapping<INode> spsmMapping) {
        int srcIndex = sourceIndex.get(source.get(0).ancestorCount() - 1);
        int tgtIndex = targetIndex.get(target.get(0).ancestorCount() - 1);

        int returnIndex = -1;

        INode sourceNode = source.get(srcIndex);

        //find the first one who is related in the same level
        for (int i = tgtIndex + 1; i < target.size(); i++) {
            INode targetNode = target.get(i);
            if (isRelated(sourceNode, targetNode, relation, mapping)) {
                setStrongestMapping(sourceNode, targetNode, mapping, spsmMapping);
                return i;
            }
        }

        //there was no correspondence between siblings in source and target lists
        //try to clean the mapping elements
        computeStrongestMappingForSource(source.get(srcIndex), mapping, spsmMapping);

        return returnIndex;
    }

    /**
     * Increments by 1 the Integer of the given list of integers at the index position.
     *
     * @param array array list of integers.
     * @param index index of the element to be incremented.
     */
    protected void inc(List<Integer> array, int index) {
        array.set(index, array.get(index) + 1);
    }

    /**
     * Checks if the given source and target elements are related considering the defined relation and the temp.
     *
     * @param source         source
     * @param target         target
     * @param relation       relation
     * @param defaultMapping original mapping
     * @return true if the relation holds between source and target, false otherwise.
     */
    protected boolean isRelated(final INode source, final INode target, final char relation, IContextMapping<INode> defaultMapping) {
        return relation == defaultMapping.getRelation(source, target);
    }

    /**
     * Sets the relation between source and target as the strongest in the temp, setting all the other relations
     * for the same source as IDK if the relations are weaker.
     *
     * @param source source node
     * @param target target node
     */
    protected void setStrongestMapping(INode source, INode target,
                                     IContextMapping<INode> mapping,
                                     IContextMapping<INode> spsmMapping
    ) {
        //if it's structure preserving
        if (isSameStructure(source, target)) {
            spsmMapping.setRelation(source, target, mapping.getRelation(source, target));

            //deletes all the less precedent relations for the same source node
            for (Iterator<INode> targetNodes = mapping.getTargetContext().nodeIterator(); targetNodes.hasNext(); ) {
                INode node = targetNodes.next();
                //if its not the target of the mapping elements and the relation is weaker
                if (source != node && mapping.getRelation(source, node) != IMappingElement.IDK
                        && isPrecedent(mapping.getRelation(source, target), mapping.getRelation(source, node))) {
                    mapping.setRelation(source, node, IMappingElement.IDK);
                }
            }

            //deletes all the less precedent relations for the same target node
            for (Iterator<INode> sourceNodes = mapping.getSourceContext().nodeIterator(); sourceNodes.hasNext(); ) {
                INode node = sourceNodes.next();
                if (target != node) {
                    mapping.setRelation(node, target, IMappingElement.IDK);
                }
            }
        } else {
            //the elements are not in the same structure, look for the correct relation
            computeStrongestMappingForSource(source, mapping, spsmMapping);
        }
    }

    /**
     * Looks for the strongest relation for the given source and sets to
     * IDK all the other mappings existing for the same source if they are less precedent.
     *
     * @param source INode to look for the strongest relation.
     */
    protected void computeStrongestMappingForSource(INode source,
                                                  IContextMapping<INode> mapping,
                                                  IContextMapping<INode> spsmMapping
    ) {
        INode strongetsRelationInTarget = null;
        List<IMappingElement<INode>> strongest = new ArrayList<>();

        //look for the strongest relation, and deletes all the non structure preserving relations
        for (Iterator<INode> targetNodes = mapping.getTargetContext().nodeIterator(); targetNodes.hasNext(); ) {
            INode j = targetNodes.next();
            if (isSameStructure(source, j)) {
                if (strongest.isEmpty() && mapping.getRelation(source, j) != IMappingElement.IDK
                        && !existsStrongerInColumn(source, j, mapping)) {
                    strongetsRelationInTarget = j;
                    strongest.add(new MappingElement<>(source, j, mapping.getRelation(source, j)));
                } else if (mapping.getRelation(source, j) != IMappingElement.IDK && !strongest.isEmpty()) {
                    int precedence = comparePrecedence(strongest.get(0).getRelation(), mapping.getRelation(source, j));
                    if (precedence == -1 && !existsStrongerInColumn(source, j, mapping)) {
                        //if target is more precedent, and there is no other stronger relation for that particular target
                        strongetsRelationInTarget = j;
                        strongest.set(0, new MappingElement<>(source, j, mapping.getRelation(source, j)));
                    }
                }
            } else {
                //they are not the same structure, function to function, variable to variable
                //delete the relation
                mapping.setRelation(source, j, IMappingElement.IDK);
            }
        }

        //if there is a strongest element, and it is different from IDK
        if (!strongest.isEmpty() && strongest.get(0).getRelation() != IMappingElement.IDK) {
            //erase all the weaker relations in the row
            for (Iterator<INode> targetNodes = mapping.getTargetContext().nodeIterator(); targetNodes.hasNext(); ) {
                INode j = targetNodes.next();
                if (j != strongetsRelationInTarget && mapping.getRelation(source, j) != IMappingElement.IDK) {
                    int precedence = comparePrecedence(strongest.get(0).getRelation(), mapping.getRelation(source, j));
                    if (precedence == 1) {
                        mapping.setRelation(source, j, IMappingElement.IDK);
                    } else if (precedence == 0) {
                        if (isSameStructure(source, j)) {
                            strongest.add(new MappingElement<>(source, j, mapping.getRelation(source, j)));
                        }
                    }
                }
            }

            //if there is more than one strongest relation
            if (strongest.size() > 1) {
                resolveStrongestMappingConflicts(source, strongest, mapping, spsmMapping);
            } else {
                //deletes all the relations in the column
                for (Iterator<INode> sourceNodes = mapping.getSourceContext().nodeIterator(); sourceNodes.hasNext(); ) {
                    INode i = sourceNodes.next();
                    if (i != source) {
                        mapping.setRelation(i, strongetsRelationInTarget, IMappingElement.IDK);
                    }
                }

                if (strongest.get(0).getRelation() != IMappingElement.IDK) {
                    spsmMapping.add(strongest.get(0));

                    // remove the relations from the same column and row
                    deleteRemainingRelationsFromMatrix(strongest.get(0), mapping);
                }
            }
        }
    }

    /**
     * Resolves conflicts in case there are more than one element with
     * the strongest relation for a given source node.
     *
     * @param source    the node for which more than one strongest relation is found
     * @param strongest the list of the strongest relations.
     */
    protected void resolveStrongestMappingConflicts(INode source,
                                                  List<IMappingElement<INode>> strongest,
                                                  IContextMapping<INode> mapping,
                                                  IContextMapping<INode> spsmMapping
    ) {
        //copy the relations to a string to log it
        int strongestIndex = -1;
        String sourceString = source.nodeData().getName().trim();

        if (log.isDebugEnabled()) {
            StringBuilder strongRelations = new StringBuilder();
            for (IMappingElement<INode> aStrongest : strongest) {
                strongRelations.append(aStrongest.getTarget().toString()).append("|");
            }

            log.debug("More than one strongest relation for " + sourceString + ": |" + strongRelations.toString());
        }

        //looks the first related node that is equal to the source node
        for (int i = 0; i < strongest.size(); i++) {
            String strongString = strongest.get(i).getTarget().nodeData().getName().trim();
            if (sourceString.equalsIgnoreCase(strongString)) {
                strongestIndex = i;
                break;
            }
        }

        //if there was no equal string, then set it to the first one
        if (strongestIndex == -1) {
            strongestIndex = 0;
        }

        if (strongest.get(strongestIndex).getRelation() != IMappingElement.IDK) {
            spsmMapping.add(strongest.get(strongestIndex));

            // Remove the relations from the same column and row
            deleteRemainingRelationsFromMatrix(strongest.get(strongestIndex), mapping);
        }
    }


    /**
     * When a given mapping element has been chosen as the strongest,
     * then delete all the other mappings from the temp by
     * setting the relation to <code>null</code>.
     *
     * @param e the strongest mapping element.
     */
    protected void deleteRemainingRelationsFromMatrix(IMappingElement<INode> e,
                                                    IContextMapping<INode> mapping) {
        //deletes all the relations in the column
        for (Iterator<INode> sourceNodes = mapping.getSourceContext().nodeIterator(); sourceNodes.hasNext(); ) {
            INode i = sourceNodes.next();
            if (i != e.getSource()) {
                mapping.setRelation(i, e.getTarget(), IMappingElement.IDK);
            }
        }

        //deletes all the relations in the row
        for (Iterator<INode> targetNodes = mapping.getTargetContext().nodeIterator(); targetNodes.hasNext(); ) {
            INode j = targetNodes.next();
            if (j != e.getTarget()) {
                mapping.setRelation(e.getSource(), j, IMappingElement.IDK);
            }
        }
    }


    /**
     * Checks if source and target are structural preserving
     * this is, function to function and argument to argument match.
     *
     * @param source source node.
     * @param target target node.
     * @return true if they are the same structure, false otherwise.
     */
    protected boolean isSameStructure(INode source, INode target) {
        boolean result = false;
        if (null != source && null != target) {
            if (source.getChildren() != null && target.getChildren() != null) {
                int sourceChildren = source.getChildren().size();
                int targetChildren = target.getChildren().size();
                if (sourceChildren == 0 && targetChildren == 0) {
                    result = true;
                } else if (sourceChildren > 0 && targetChildren > 0) {
                    result = true;
                }
            } else if (source.getChildren() == null && target.getChildren() == null) {
                result = true;
            }
        } else if (null == source && null == target) {
            result = true;
        }
        return result;
    }

    /**
     * Checks if there is no other stronger relation in the same column.
     *
     * @param source source node
     * @param target target node
     * @return true if exists stronger relation in the same column, false otherwise.
     */
    protected boolean existsStrongerInColumn(INode source, INode target,
                                           IContextMapping<INode> mapping) {
        boolean result = false;

        char current = mapping.getRelation(source, target);

        //compare with the other relations in the column
        for (Iterator<INode> sourceNodes = mapping.getSourceContext().nodeIterator(); sourceNodes.hasNext(); ) {
            INode i = sourceNodes.next();
            if (i != source && mapping.getRelation(i, target) != IMappingElement.IDK
                    && isPrecedent(mapping.getRelation(i, target), current)) {
                result = true;
                break;
            }
        }

        return result;
    }


    /**
     * Checks if the semantic relation of the source is more important, in the order of precedence
     * than the one in the target, the order of precedence is, = > < ?.
     *
     * @param source source relation.
     * @param target target relation.
     * @return true if source is more precedent than target, false otherwise.
     */
    protected boolean isPrecedent(char source, char target) {
        return comparePrecedence(source, target) == 1;
    }


    /**
     * Compares the semantic relation of the source and target in the order of precedence
     * = > < ! ?. Returning -1 if sourceRelation is less precedent than targetRelation,
     * 0 if sourceRelation is equally precedent than targetRelation,
     * 1 if sourceRelation  is more precedent than targetRelation.
     *
     * @param sourceRelation source relation from IMappingElement.
     * @param targetRelation target relation from IMappingElement.
     * @return -1 if sourceRelation is less precedent than targetRelation,
     * 0 if sourceRelation is equally precedent than targetRelation,
     * 1 if sourceRelation  is more precedent than targetRelation.
     */
    protected int comparePrecedence(char sourceRelation, char targetRelation) {
        int result;

        int sourcePrecedence = getPrecedenceNumber(sourceRelation);
        int targetPrecedence = getPrecedenceNumber(targetRelation);
        if (sourcePrecedence < targetPrecedence) {
            result = 1;
        } else if (sourcePrecedence == targetPrecedence) {
            result = 0;
        } else {
            result = -1;
        }

        return result;
    }


    /**
     * Gives the precedence order for the given semanticRelation defined in IMappingElement.
     * EQUIVALENT_TO = 1
     * MORE_GENERAL = 2
     * LESS_GENERAL = 3
     * DISJOINT_FROM = 4
     * IDK = 5
     *
     * @param semanticRelation the semantic relation as defined in IMappingElement.
     * @return the order of precedence for the given relation, Integer.MAX_VALUE if the relation
     * is not recognized.
     */
    protected int getPrecedenceNumber(char semanticRelation) {

        //initializes the precedence number to the least precedent
        int precedence = Integer.MAX_VALUE;

        if (semanticRelation == IMappingElement.EQUIVALENCE) {
            precedence = 1;
        } else if (semanticRelation == IMappingElement.MORE_GENERAL) {
            precedence = 2;
        } else if (semanticRelation == IMappingElement.LESS_GENERAL) {
            precedence = 3;
        } else if (semanticRelation == IMappingElement.DISJOINT) {
            precedence = 4;
        } else if (semanticRelation == IMappingElement.IDK) {
            precedence = 5;
        }

        return precedence;
    }
}