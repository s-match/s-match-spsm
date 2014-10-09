package it.unitn.disi.smatch.matchers.structure.tree.spsm;

import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.mappings.IMappingFactory;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.filters.IMappingFilter;
import it.unitn.disi.smatch.filters.MappingFilterException;
import it.unitn.disi.smatch.matchers.structure.node.INodeMatcher;
import it.unitn.disi.smatch.matchers.structure.tree.TreeMatcherException;
import it.unitn.disi.smatch.matchers.structure.tree.def.DefaultTreeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Used the DefaultTreeMatcher for computing the default set of mapping elements and
 * then calls the TreeMatcher.SPSMTreeMatcher.spsmFilter property where the filtering
 * and computation of the similarity score is performed.
 * The filters returns the set of mapping elements to preserve a set of structural properties,
 * namely:
 * <ul>
 * <li> one-to-one correspondences between semantically related nodes,
 * <li> leaf nodes are matched to leaf nodes and internal nodes are matched to internal nodes.
 * </ul>
 * <p/>
 * For further details refer to:
 * Approximate structure-preserving semantic matching
 * by
 * Giunchiglia, Fausto and McNeill, Fiona and Yatskevich, Mikalai and
 * Pane, Juan and Besana, Paolo and Shvaiko, Pavel (2008)
 * <a href="http://eprints.biblio.unitn.it/archive/00001459/">http://eprints.biblio.unitn.it/archive/00001459/</a>
 *
 * @author Juan Pane pane@disi.unitn.it
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class SPSMTreeMatcher extends DefaultTreeMatcher {

    private final static Logger log = LoggerFactory.getLogger(SPSMTreeMatcher.class.getName());

    protected final IMappingFilter spsmFilter;

    public SPSMTreeMatcher(INodeMatcher nodeMatcher, IMappingFactory mappingFactory, IMappingFilter spsmFilter) {
        super(nodeMatcher, mappingFactory);
        this.spsmFilter = spsmFilter;
    }

    @Override
    public IContextMapping<INode> treeMatch(IContext sourceContext,
                                            IContext targetContext,
                                            IContextMapping<IAtomicConceptOfLabel> acolMapping)
            throws TreeMatcherException {

        IContextMapping<INode> defaultMappings = super.treeMatch(sourceContext, targetContext, acolMapping);
        try {

            return spsmFilter.filter(defaultMappings);
        } catch (MappingFilterException e) {
            log.info("Problem matching source[" + getFNSignatureFromIContext(sourceContext.getRoot()) + "] to target [" + getFNSignatureFromIContext(targetContext.getRoot()) + "]");
            log.info(e.getMessage());
            log.info(SPSMTreeMatcher.class.getName(), e);

            throw (new TreeMatcherException(e.getMessage(), e.getCause()));

        }
    }


    /**
     * Creates a function-like tree from the given root node
     *
     * @param node the root node
     * @return the string representation for the given tree in function-like representation
     */
    private String getFNSignatureFromIContext(INode node) {

        String ret = node.nodeData().getName();
        List<INode> children = node.getChildren();
        if (children != null && children.size() > 0) {
            ret += "(";
            for (INode aChildren : children) {
                ret += getFNSignatureFromIContext(aChildren) + ",";
            }

            ret = ret.substring(0, ret.length() - 1);
            ret += ")";
        }
        return ret;

    }
}