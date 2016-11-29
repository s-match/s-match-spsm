package it.unitn.disi.smatch.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.mappings.IMappingFactory;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.matchers.structure.tree.spsm.ted.TreeEditDistance;
import it.unitn.disi.smatch.matchers.structure.tree.spsm.ted.utils.impl.MatchedTreeNodeComparator;
import it.unitn.disi.smatch.matchers.structure.tree.spsm.ted.utils.impl.WorstCaseDistanceConversion;


/**
 * This is a modified version of SPSM that treats the two trees asymmetrically:
 * the source tree is considered to be a query schema while the target tree
 * is a reference schema. The mapping score is computed based on the extent
 * the latter covers the former. In other words, it is not dependent on the size
 * of the reference schema tree.
 *
 * @author Gabor BELLA, gabor.bella@unitn.it
 * @since 2.0.0
 */
public class AsymmetricSPSMMappingFilter extends SPSMMappingFilter {

    private static final Logger log = LoggerFactory.getLogger(AsymmetricSPSMMappingFilter.class);

    public AsymmetricSPSMMappingFilter(IMappingFactory mappingFactory) {
        super(mappingFactory);
    }

    public AsymmetricSPSMMappingFilter(IMappingFactory mappingFactory, IContextMapping<INode> mapping) {
        super(mappingFactory, mapping);
    }

    /**
     * Computes the similarity score in an asymmetrical manner that only depends
     * on the size of the source (query) tree.
     * Insert weight is set to 0 for asymmetric mapping (do not count 
     * in the distance the nodes that are present in the reference tree 
     * but are absent from the query tree (and thus need to be inserted).
     * 
     * @param mapping mapping
     * @return similarity score
     */
    @Override
    protected double computeSimilarity(IContextMapping<INode> mapping) {
        MatchedTreeNodeComparator mntc = new MatchedTreeNodeComparator(mapping);
        TreeEditDistance tde = new TreeEditDistance(mapping.getSourceContext(), 
                                    mapping.getTargetContext(), mntc, 
                                    new WorstCaseDistanceConversion(),
                                    TreeEditDistance.DEFAULT_PATH_LENGTH_LIMIT,
                                    0d,
                                    TreeEditDistance.DEFAULT_WEIGHT_DELETE,
                                    TreeEditDistance.DEFAULT_WEIGHT_SUBSTITUTE);

        tde.calculate();
        double ed = tde.getTreeEditDistance();

        return 1 - (ed / mapping.getSourceContext().nodesCount());
    }

}