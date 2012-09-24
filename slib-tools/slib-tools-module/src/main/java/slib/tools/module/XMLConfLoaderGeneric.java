package slib.tools.module;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import slib.sglib.algo.utils.GAction;
import slib.sglib.algo.utils.GActionType;
import slib.sglib.io.conf.GDataConf;
import slib.sglib.io.conf.GraphConf;
import slib.sglib.io.loader.csv.CSV_Mapping;
import slib.sglib.io.loader.csv.CSV_StatementTemplate;
import slib.sglib.io.loader.csv.CSV_StatementTemplate_Constraint;
import slib.sglib.io.loader.csv.StatementTemplateElement;
import slib.sglib.io.loader.csv.StatementTemplate_Constraint_Type;
import slib.sglib.io.loader.utils.filter.graph.Filter;
import slib.sglib.io.util.GFormat;
import slib.sglib.model.graph.elements.type.VType;
import slib.sglib.model.repo.impl.DataRepository;
import slib.utils.ex.SGL_Ex_Critic;
import slib.utils.i.Conf;
import slib.utils.i.Parametrable;
import slib.utils.impl.Util;
import slib.utils.threads.ThreadManager;

public class XMLConfLoaderGeneric {

	private String xmlFile;
	private Document document;
	private DataRepository dataRepo;


	private LinkedList<GraphConf> graphConfs;
	private LinkedHashSet<Filter> 	filters;

	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	static final Map<String, VType> admittedVType = new HashMap<String, VType>();
    static {
    	admittedVType.put("CLASS"     , VType.CLASS);
    	admittedVType.put("INSTANCE"  , VType.INSTANCE);
    	admittedVType.put("LITERAL"   , VType.LITERAL);
    	admittedVType.put("UNDEFINED" , VType.UNDEFINED);
    }
    
	static final Map<String, URI> admittedPType = new HashMap<String, URI>();
    static {
    	admittedPType.put("RDF.TYPE"     	  , RDF.TYPE);
    	admittedPType.put("RDFS.SUBCLASSOF"   , RDFS.SUBCLASSOF);
    }
    

    

	public XMLConfLoaderGeneric(String xmlFile) throws SGL_Ex_Critic{


		dataRepo = DataRepository.getSingleton();

		this.xmlFile = xmlFile;
		graphConfs = new LinkedList<GraphConf>();
		filters    = new LinkedHashSet<Filter>();

		load();
	}

	private void load() throws SGL_Ex_Critic{
		logger.info("Loading XML conf from : "+xmlFile);


		try {

			DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder(); 
			document = parser.parse(new File(xmlFile )); 


			NodeList opt = document.getElementsByTagName(XmlTags.OPT_TAG);

			//------------------------------
			//	 Load General Option 
			//------------------------------
			logger.debug("Loading options");

			if(	opt.getLength() == 1 && opt.item(0) instanceof Element )
				extractOptConf(GenericConfBuilder.build((Element) opt.item(0)));

			else if(opt.getLength() > 1)
				Util.error("Only one "+XmlTags.OPT_TAG+" tag allowed");




			//------------------------------
			//	 Load Variables 
			//------------------------------
			logger.debug("Loading variables");
			NodeList variablesConfig = document.getElementsByTagName(XmlTags.VARIABLES_TAG);

			if(	variablesConfig.getLength() == 1 && variablesConfig.item(0) instanceof Element )
				loadVariablesConf((Element) variablesConfig.item(0));

			else if(variablesConfig.getLength() > 0)
				Util.error("Only one "+XmlTags.VARIABLES_TAG+" is admitted");

			//------------------------------
			//	 Load Name space Option 
			//------------------------------

			NodeList namespaces = document.getElementsByTagName(XmlTags.NAMESPACES_TAG);

			if(	namespaces.getLength() == 1 && namespaces.item(0) instanceof Element )
				loadNamespaces((Element) namespaces.item(0));

			else if(namespaces.getLength() > 1)
				Util.error("Only one "+XmlTags.NAMESPACES_TAG+" tag allowed");



			//------------------------------
			//	 Load Graph Information
			//------------------------------

			logger.debug("Loading graph configurations");

			NodeList graphsConfig = document.getElementsByTagName(XmlTags.GRAPHS_TAG);


			// Check number of graph specification + critical attributes
			if(	graphsConfig.getLength() == 1 && graphsConfig.item(0) instanceof Element ){

				NodeList nListGConf = ((Element) graphsConfig.item(0)).getElementsByTagName(XmlTags.GRAPH_TAG);


				for (int i = 0; i < nListGConf.getLength(); i++) {
					Element gConf = (Element) nListGConf.item(i);
					loadGraphConf( gConf );
				}
			}
			else
				Util.error(XMLConstUtils.ERROR_NB_GRAPHS_SPEC);


			//------------------------------
			//	 Load Filters Specification
			//------------------------------

			logger.debug("Loading filters");
			NodeList filtersElement = document.getElementsByTagName(XmlTags.FILTERS_TAG);

			if(	filtersElement.getLength() == 1 && filtersElement.item(0) instanceof Element )
				loadFiltersConf((Element) filtersElement.item(0));

			else if(filtersElement.getLength() > 0)
				Util.error("Only one "+XmlTags.FILTERS_TAG+" is admitted");


		} catch (Exception e){
			throw new SGL_Ex_Critic(e.getMessage());
		}


		logger.info("generic configuration loaded ");
		//		logger.info("- Graph conf loaded : "+graphConfs.size());

	}


	private void loadNamespaces(Element item) throws SGL_Ex_Critic {

		NodeList list = item.getElementsByTagName(XmlTags.NAMESPACE_TAG);

		for(int i = 0; i < list.getLength(); i++){

			Conf m = GenericConfBuilder.build( (Element) list.item(i) );

			String prefix   = (String) m.getParam(XmlTags.NS_ATTR_PREFIX);
			String ref = (String) m.getParam(XmlTags.NS_ATTR_REF);

			if(prefix == null)
				throw new SGL_Ex_Critic("Invalid "+XmlTags.NAMESPACE_TAG+" tag, missing a "+XmlTags.NS_ATTR_PREFIX+" attribut");
			else if(ref == null)
				throw new SGL_Ex_Critic("Invalid "+XmlTags.NAMESPACE_TAG+" tag, missing a "+XmlTags.NS_ATTR_REF+" attribut associated to variable "+prefix);

			logger.info("add namespace prefix : "+prefix+" ref : "+ref);
			DataRepository.getSingleton().loadNamespacePrefix(prefix, ref);
		}
	}

	private void loadGraphConf(Element item) throws SGL_Ex_Critic {
		logger.debug("Loading graph conf");

		GraphConf gconf = new GraphConf();

		// URI

		String uris = item.getAttribute("uri");
		uris = GenericConfBuilder.applyGlobalPatterns(uris);

		logger.debug("uri: "+uris);

		URI uri = dataRepo.createURI(uris);
		gconf.setUri(uri);


		// Load Data
		String[] graphDataFileDefAtt = {"format","path"};

		NodeList nListGdata = document.getElementsByTagName(XmlTags.DATA_TAG);

		if(	nListGdata.getLength() == 1 && nListGdata.item(0) instanceof Element ){

			NodeList nListGConf = ((Element) nListGdata.item(0)).getElementsByTagName(XmlTags.FILE_TAG);

			for (int i = 0; i < nListGConf.getLength(); i++) {
				Element dataConf = (Element) nListGConf.item(i);

				Conf conf = GenericConfBuilder.build(dataConf);

				logger.debug("> data conf");

				// Data Format

				String format = conf.getParamAsString("format");
				logger.debug("- format: "+format);

				GFormat gFormat = XMLAttributMapping.GDataFormatMapping.get(format); 

				if(gFormat == null)
					throw new SGL_Ex_Critic("Unknow data format "+format+", valids "+XMLAttributMapping.GDataFormatMapping.keySet());

				// Data Location

				String path = conf.getParamAsString("path");
				logger.debug("- path: "+path);

				GDataConf gDataConf = new GDataConf(gFormat, path);

				// Add Extra Parameters
				loadExtraParameters(conf, graphDataFileDefAtt, gDataConf);
				
				// Additional processing
				gDataConf = gDataConfAdditional(gFormat, dataConf, gDataConf);

				gconf.addGDataConf(gDataConf);

				logger.debug("");
			}
		}
		else
			Util.error(XMLConstUtils.ERROR_NB_DATA_SPEC);

		// Load Actions
		String[] graphActionDefAtt = {"type"};

		NodeList nListGactions = document.getElementsByTagName(XmlTags.ACTIONS_TAG);

		if(	nListGactions.getLength() == 1 && nListGactions.item(0) instanceof Element ){

			NodeList nListGConf = ((Element) nListGactions.item(0)).getElementsByTagName(XmlTags.ACTION_TAG);

			for (int i = 0; i < nListGConf.getLength(); i++) {



				Element xmlConf = (Element) nListGConf.item(i);
				Conf conf = GenericConfBuilder.build(xmlConf);

				logger.debug("> action conf");

				String type = conf.getParamAsString("type");
				logger.debug("- type: "+type);

				GActionType gType = XMLAttributMapping.GActionTypeMapping.get(type); 

				if(gType == null)
					throw new SGL_Ex_Critic("Unknow action type "+type+", accepted "+XMLAttributMapping.GActionTypeMapping.keySet());

				GAction gAction = new GAction(gType);

				// Add Extra Parameters
				loadExtraParameters(conf, graphActionDefAtt, gAction);

				gconf.addGAction(gAction);

				logger.debug("");
			}
		}
		else if(nListGactions.getLength() > 1)
			Util.error(XMLConstUtils.ERROR_NB_ACTIONS_SPEC);

		graphConfs.add(gconf);

	}

	private GDataConf gDataConfAdditional(GFormat gFormat, Element dataConf, GDataConf gDataConf) throws SGL_Ex_Critic {
		
		if(gFormat == GFormat.CSV){
			
			
			final Map<String, StatementTemplateElement> admittedStmConstraintElement = new HashMap<String, StatementTemplateElement>();
		    {
		    	admittedStmConstraintElement.put("subject"     	  , StatementTemplateElement.SUBJECT);
		    	admittedStmConstraintElement.put("object"     	  , StatementTemplateElement.OBJECT);
		    }
		    
			final Map<String, StatementTemplate_Constraint_Type> admittedStmConstraintType = new HashMap<String, StatementTemplate_Constraint_Type>();
		    {
		    	admittedStmConstraintType.put("EXISTS"     	  , StatementTemplate_Constraint_Type.EXISTS);
		    }
		    
			
			HashMap<Integer, CSV_Mapping> mappings = new HashMap<Integer, CSV_Mapping>();
			HashMap<Integer, CSV_StatementTemplate> stmtemplates = new HashMap<Integer, CSV_StatementTemplate>();
			
			// mappings
			
			NodeList list = dataConf.getElementsByTagName(XmlTags.MAP_TAG);
			
			for (int i = 0; i < list.getLength(); i++) {

				Element xmlConf = (Element) list.item(i);
				Conf conf = GenericConfBuilder.build(xmlConf);
				
				Integer field = Util.stringToInteger( (String) conf.getParam(XmlTags.MAP_ATT_FIELD) );
				String type   = (String) conf.getParam(XmlTags.MAP_ATT_TYPE);
				String prefix = (String) conf.getParam(XmlTags.MAP_ATT_PREFIX);
				
				if(field == null)
					throw new SGL_Ex_Critic("Cannot state field number associated to mapping definition in CSV configuration");
				
				if(! admittedVType.containsKey(type))
					throw new SGL_Ex_Critic("Cannot state type "+type+" associated to mapping definition in CSV configuration, admitted "+admittedVType.keySet());
				
				VType vtype = admittedVType.get(type);
				
				CSV_Mapping m = new CSV_Mapping(field, vtype, prefix);
				mappings.put(field, m);
			}
			
			gDataConf.addParameter("mappings", mappings);
			
			
			// statement template
			list = dataConf.getElementsByTagName(XmlTags.STM_TAG);
			
			for (int i = 0; i < list.getLength(); i++) {

				Element xmlConf = (Element) list.item(i);
				Conf conf = GenericConfBuilder.build(xmlConf);
				
				Integer s_id = Util.stringToInteger( (String) conf.getParam(XmlTags.STM_ATT_SUBJECT) );
				Integer o_id = Util.stringToInteger( (String) conf.getParam(XmlTags.STM_ATT_OBJECT) );
				
				String p_string = (String) conf.getParam(XmlTags.STM_ATT_PREDICATE);
				
				if(s_id == null)
					throw new SGL_Ex_Critic("Cannot state number associated to subject statement template in CSV configuration");
				
				if(o_id == null)
					throw new SGL_Ex_Critic("Cannot state number associated to object statement template in CSV configuration");
				
				if(p_string == null)
					throw new SGL_Ex_Critic("Cannot state number associated to predicate statement template in CSV configuration");
				
				URI p = null;
				
				if(admittedPType.containsKey(p_string))
					p = admittedPType.get(p_string);
				else
					p = dataRepo.createURI(p_string);
				
				
				CSV_StatementTemplate m = new CSV_StatementTemplate(s_id, o_id, p);
				
				// statement constraint
				NodeList listinner = dataConf.getElementsByTagName(XmlTags.STM_CONSTRAINT_TAG);
				
				for (int j = 0; j < listinner.getLength(); j++) {
					
					Conf confinner = GenericConfBuilder.build( (Element) listinner.item(j) );
					String element    = (String) confinner.getParam(XmlTags.STM_CONSTRAINT_ATT_ELEMENT);
					String typeString = (String) confinner.getParam(XmlTags.STM_CONSTRAINT_ATT_TYPE);
					
					StatementTemplateElement elem = admittedStmConstraintElement.get(element);
					StatementTemplate_Constraint_Type type = admittedStmConstraintType.get(typeString);
					
					if(elem == null)
						throw new SGL_Ex_Critic("Cannot state element "+element+" associated to statement constraint definition in CSV configuration, admitted "+admittedStmConstraintElement.keySet());
					if(type == null)
						throw new SGL_Ex_Critic("Cannot state type "+typeString+" associated to statement constraint definition in CSV configuration, admitted "+admittedStmConstraintType.keySet());
					
					CSV_StatementTemplate_Constraint constraint = new CSV_StatementTemplate_Constraint(elem,type);
					m.addConstraint(constraint);
				}
				
				stmtemplates.put(s_id,m);
			}
			
			gDataConf.addParameter("statementTemplates", stmtemplates);
		}
		
		return gDataConf;
	}

	private void loadExtraParameters(Conf conf, String[] restrictions, Parametrable p){


		Map<String, Object> map = conf.getParams();

		for(String attName : map.keySet()){


			String attValue = conf.getParamAsString(attName);

			boolean toLoad = true;
			for(String s : restrictions){
				if(attName.equals(s)){
					toLoad = false;
					break;
				}
			}

			if(!toLoad) continue;

			logger.debug("- "+attName+": "+attValue);
			p.addParameter(attName, attValue);
		}
	}

	private void loadVariablesConf(Element item) throws SGL_Ex_Critic {

		NodeList list = item.getElementsByTagName(XmlTags.VARIABLE_TAG);

		GlobalConfPattern userConf = GlobalConfPattern.getInstance();

		for(int i = 0; i < list.getLength(); i++){

			Conf m = GenericConfBuilder.build( (Element) list.item(i) );

			String key   = (String) m.getParam(XmlTags.KEY_ATTR);
			String value = (String) m.getParam(XmlTags.VALUE_ATTR);

			if(key == null)
				throw new SGL_Ex_Critic("Invalid "+XmlTags.VARIABLE_TAG+" tag, missing a "+XmlTags.KEY_ATTR+" attribut");
			else if(value == null)
				throw new SGL_Ex_Critic("Invalid "+XmlTags.VARIABLE_TAG+" tag, missing a "+XmlTags.VALUE_ATTR+" attribut associated to variable "+key);

			logger.info("add variable key : {"+key+"} value : "+value);
			userConf.addVar(key, value);
		}
	}





	private void loadFiltersConf(Element item) throws SGL_Ex_Critic {

		NodeList list = item.getElementsByTagName(XmlTags.FILTER_TAG);
		LinkedHashSet<Conf> gConfGenerics = GenericConfBuilder.build(list);
		filters = buildFilters(gConfGenerics);

	}

	private LinkedHashSet<Filter> buildFilters( LinkedHashSet<Conf> gConfGenerics) throws SGL_Ex_Critic {

		for(Conf c: gConfGenerics){

			Filter f = FilterBuilderGeneric.buildFilter(c);

			// check duplicate filter id
			for(Filter ft: filters){
				if(ft.getId().equals(f.getId()))
					throw new SGL_Ex_Critic("Duplicate id '"+f.getId()+"' found in filter specification");
			}
			filters.add( f );
		}

		return filters;
	}

	private void extractOptConf(Conf gc) throws SGL_Ex_Critic {

		String nbThread_s   	 = (String) gc.getParam(XmlTags.OPT_NB_THREADS_ATTR);
		if(nbThread_s != null){
			try{
				int nbThreads = Integer.parseInt(nbThread_s);
				ThreadManager.getSingleton().setMaxThread(nbThreads);
			}
			catch(NumberFormatException e){
				throw new SGL_Ex_Critic("Error converting "+XmlTags.OPT_NB_THREADS_ATTR+" to integer value ");
			}
		}	
	}


	public LinkedList<GraphConf> getGraphConfs() {
		return graphConfs;
	}

	public LinkedHashSet<Filter> getFilters() {
		return filters;
	}


}