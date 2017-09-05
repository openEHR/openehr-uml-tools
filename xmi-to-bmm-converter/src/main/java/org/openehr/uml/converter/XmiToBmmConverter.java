package org.openehr.uml.converter;

/*
 * #%L
 * OpenEHR - OpenEHR UML Tools
 * %%
 * Copyright (C) 2016 - 2017 Cognitive Medical Systems
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 * Author: Claude Nanjo
 */

import guru.mwangaza.eap.xmi.reader.XmiReader;
import guru.mwangaza.uml.*;
import org.openehr.uml.converter.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openehr.bmm.*;
import org.openehr.bmm.persistence.*;

import java.util.*;

/**
 * A utility to convert an XMI file to the OpenEHR Basic Metamodel
 * format.
 *
 * Created by cnanjo on 10/2/16.
 */
public class XmiToBmmConverter {

    public static final String REDEFINED_DATATYPE_PKG = "Redefined_Datatypes";

    private Logger logger;
    private List<PersistedBmmSchema> bmmSchemas;


    public XmiToBmmConverter() {
        bmmSchemas = new ArrayList<>();
        logger =  LogManager.getLogger(XmiToBmmConverter.class);
    }

    /**
     * Methods returns the BMM schemas generated by the
     * routine.
     *
     * @return
     */
    public List<PersistedBmmSchema> getBmmSchemas() {
        return bmmSchemas;
    }

    /**
     * Method generates the BMM schemas for the corresponding UML projects
     * in the order they are listed in the config.xml file. Relations between UML
     * projects are represented in the BMM using the BMM import directive.
     * <br/>
     *
     * //TODO Handle BMM dependencies
     * //TODO Should this be renamed to aml-to-bmm-converter only considering AML profiles?
     *
     * @param config
     * @return
     */
    public List<PersistedBmmSchema> convert(Configuration config) {
        Map<String, UmlModel> dependencies = new HashMap<String, UmlModel>();

        for (int index = 0; index < config.getFilePaths().size(); index++) {
            String name = config.getFileNames().get(index);
            String path = config.getFilePaths().get(index);

            logger.info("Processing XMI file " + name);

            XmiReader loader = XmiReader.configureDefaultMagicDrawXmiReader();
            if (index > 0) {
                loader.setDependencies(dependencies);
            }
            UmlModel model = loader.loadFromFilePath(path);
            model.buildIndex();

            if (index < config.getFilePaths().size() - 1) {
                dependencies.put(name, model);
            }

            PersistedBmmSchema bmmSchema = convertToBmm(name, model);
            for(PersistedBmmSchema schema: bmmSchemas) {
                BmmIncludeSpecification includeSpecification = new BmmIncludeSpecification(schema.generateSchemaIdentifier());
                bmmSchema.addInclude(includeSpecification);
            }
            bmmSchemas.add(bmmSchema);
        }
        return bmmSchemas;
    }

    /**
     * Method takes a UmlModel instance and translates it to its corresponding BMM schema object.
     * Note that at this time, this routine is optimized for CIMI and makes use of the AML profiles
     * only.
     *
     * @param name
     * @param umlModel
     * @return
     */
    public PersistedBmmSchema convertToBmm(String name, UmlModel umlModel) {
        PersistedBmmSchema schema = new PersistedBmmSchema();
        handleSchemaDocumentation(name, schema);
        //schema.setArchetypeRmClosurePackages(); -- Not sure how I would know this unless this is noted in the UML model //TODO Fix as recommended by Harold and list all children packages. Check spec to be sure
        UmlPackage rootPackage = umlModel.getPackages().get(0); //TODO Another argument to make this a CIMI-AML routine rather than a general UML routine. A convention adopted here is that the top level package is always the reference model and has only package content
        handleReferenceModelProfileAndTaggedValues(rootPackage, schema);
        List<UmlPackage> umlPackages = umlModel.getPackages();
        PersistedBmmPackage topLevelPackage = new PersistedBmmPackage();
        topLevelPackage.setName(umlPackages.get(0).getName().replaceAll(" ", "_"));//Convention that first-level package is parent container
        schema.addPackage(topLevelPackage);
        handlePackages(schema, umlPackages, topLevelPackage);
        return schema;
    }

    /**
     * Method handles the Schema Documentation section of the BMM
     *
     * @param umlModelName
     * @param schema
     */
    public void handleSchemaDocumentation(String umlModelName, PersistedBmmSchema schema) {
        schema.setSchemaRevision(new Date().toString()); //TODO Figure out what field to read in the XMI or whether this should belong in the config.xml file.
        schema.setSchemaLifecycleState("dstu"); //TODO Probably move this to the config.xml file.
        schema.setSchemaDescription(umlModelName + " - Schema generated from UML");
    }


    /**
     * Method handles the Schema Identification section of the BMM file. It does so by
     * leveraging the AML Reference Profile decoration on the root package of the model.
     * In particular, it looks for the tagged values of:
     * <ol>
     *     <li>rmPublisher</li>
     *     <li>rmVersion</li>
     *     <li>rmNamespace</li>
     * </ol>
     *
     *
     * Precondition: UML model applies AML profiles
     * Precondition: UML model defines a single top-level package for the reference model. All content is contained in children packages
     * Precondition: Tagged values 'rmPublisher', 'rmVersion', and 'rmNamespace' are added to decorated UML element.
     *
     * @param rootPackage
     * @param target
     * @return
     */
    public void handleReferenceModelProfileAndTaggedValues(UmlPackage rootPackage, PersistedBmmSchema target) {
        if (rootPackage != null && rootPackage.getStereotypes().size() == 1) {
            UmlStereotype stereotype = rootPackage.getStereotypes().get("ReferenceModel").get(0);
            String rmPublisher = stereotype.getTaggedValue("rmPublisher").getValue();
            String rmVersion = stereotype.getTaggedValue("rmVersion").getValue();
            String rmNamespace = stereotype.getTaggedValue("rmNamespace").getValue();
            target.setRmPublisher(rmPublisher);
            target.setRmRelease(rmVersion);
            target.setSchemaName(rmNamespace.replaceAll(" ", "-"));
        }
    }

    /**
     * Method handles the translation of UML Reference Model packages contained within the parent Reference Model
     * package (decorated with the Reference Model stereotype). Method first builds a PackageContainer and then adds
     * each leaf package definition to that container. Note that this method will collapse a package hierarchy.
     * During the conversion process, all spaces in package names will be replaced with underscores.
     *
     * Note that, at this time, only a single Package Container is supported.
     *
     * Precondition: Only two levels of packages is supported at this time. That is, a parent package identifying the reference model and the children package content.
     * TODO Generalize to arbitrarily nested packages and more than a single package container? Discuss with team.
     *
     * @param schema The target BMM Schema instance being constructed
     * @param umlPackages - UML packages read from the source XMI file
     * @param packageContainer - The package container that will hold the children packages
     */
    private void handlePackages(PersistedBmmSchema schema, List<UmlPackage> umlPackages, PersistedBmmPackage packageContainer) {
        for (UmlPackage umlPackage : umlPackages) {
            if (umlPackage.getPackages() != null && umlPackage.getPackages().size() > 0) {
                handlePackages(schema, umlPackage.getPackages(), packageContainer);
                continue;
            }
            PersistedBmmPackage bmmPackage = new PersistedBmmPackage(umlPackage.getName().replaceAll(" ", "_"));

            bmmPackage.setDocumentation(umlPackage.getDocumentation());

            //Excludes redefined types such as DateTimeInterval = INTERVAL_VALUE<DATE_TIME>
            if(bmmPackage.getName().equals(REDEFINED_DATATYPE_PKG)) {
                continue;
            }
            packageContainer.addPackage(bmmPackage);
            for (UmlClass umlClass : umlPackage.getClasses()) {
                handleClass(schema, bmmPackage, umlClass);
            }
            schema.getArchetypeRmClosurePackages().add(packageContainer.getName().replaceAll(" ", "_") + "." + umlPackage.getName().replaceAll(" ", "_"));
        }
    }

    /**
     * Method handles the proper conversion of UML classes into their BMM equivalents.
     *
     * @param schema
     * @param bmmPackage
     * @param umlClass
     */
    private void handleClass(PersistedBmmSchema schema, PersistedBmmPackage bmmPackage, UmlClass umlClass) {
        PersistedBmmClass bmmClass = null;
        if (umlClass.isGenericType()) {
            bmmClass = handleGenericClasses(umlClass);
        } else {
            bmmClass = new PersistedBmmClass();
        }
        bmmClass.setName(umlClass.getName());
        bmmClass.setDocumentation(umlClass.getDocumentation());
        if(bmmClass.getName().equalsIgnoreCase("")) {
            System.out.println("Boolean");
        }
        bmmPackage.addClass(umlClass.getName());
        List<UmlProperty> properties = umlClass.getProperties();
        for (UmlProperty umlProperty : properties) {
            BmmMultiplicityInterval bmmCardinality = new BmmMultiplicityInterval();
            if (umlProperty.getLow() == null) {
                bmmCardinality.setLow(1);
            } else {
                bmmCardinality.setLow(umlProperty.getLow());
            }
            if (umlProperty.getHigh() == null) {
                bmmCardinality.setHigh(1);
            } else if (umlProperty.getHigh() < 0) {
                bmmCardinality.setExcludeUpperBound(true);
            } else {
                bmmCardinality.setHigh(umlProperty.getHigh());
            }
            PersistedBmmProperty property = null;
            if (bmmCardinality.getHigh() == null || bmmCardinality.getHigh().getAsInteger() > 1) {
                property = new PersistedBmmContainerProperty();
                if(umlProperty.getFirstType().getTemplateBinding() == null) {
                    ((PersistedBmmContainerProperty) property).setTypeDefinition(new PersistedBmmContainerType("List", umlProperty.getFirstType().getName()));
                } else {
                    PersistedBmmContainerType containerType = new PersistedBmmContainerType();
                    containerType.setContainerType("List");
                    PersistedBmmGenericType genericType = handleParameterBindings(umlProperty);
                    containerType.setTypeDefinition(genericType);
                    ((PersistedBmmContainerProperty) property).setTypeDefinition(containerType);
                }
                ((PersistedBmmContainerProperty) property).setCardinality(bmmCardinality);
            }  else if(umlProperty.getFirstType().getTemplateBinding() != null){
                property = new PersistedBmmGenericProperty();
                PersistedBmmGenericType genericType = handleParameterBindings(umlProperty);
                property.setTypeDefinition(genericType);
            } else if(umlProperty.isGenericType()) {
                property = new PersistedBmmSinglePropertyOpen();
                ((PersistedBmmSinglePropertyOpen)property).setType(umlProperty.getFirstType().getName());
            } else {
                property = new PersistedBmmSingleProperty();
                ((PersistedBmmSingleProperty)property).setType(umlProperty.getFirstType().getName());
            }
            if (bmmCardinality.getLow() != null && bmmCardinality.getLow().getAsInteger() >= 1) {
                property.setMandatory(true);
            }
            property.setName(umlProperty.getName());
            property.setDocumentation(umlProperty.getDocumentation());
            bmmClass.addProperty(property);
        }
        List<UmlClass> generalizations = umlClass.getGeneralizations();
        for (UmlClass generalization : generalizations) {
            PersistedBmmClass bmmGeneralization = new PersistedBmmClass();
            bmmGeneralization.setName(generalization.getName());
            bmmClass.addAncestor(generalization.getName());
        }
        bmmClass.setAbstract(umlClass.isAbstract());
        if (bmmPackage.getName().equalsIgnoreCase("Primitive_Types")) {
            schema.addPrimitive(bmmClass);
        } else {
            schema.addClassDefinition(bmmClass);
        }
    }

    /**
     * If a generic parameter is bound to a type, this method will return the appropriate generic type. E.g.,
     * If you have a generic type such as INTERVAL_VALUE&lt;T extends ORDERED&gt; and wish to bind T to DATE_TIME in
     * order to have INTERVAL_VALUE&lt;DATE_TIME&gt;, this method will translate the XMI representation of the binding
     * to the form desired.
     *
     * TODO Handle recursive nature of bindings such as TYPE1&lt;TYPE2&lt;TYPE3&lt;...&gt;&gt;&gt;
     *
     * @param umlProperty
     * @return
     */
    private PersistedBmmGenericType handleParameterBindings(UmlProperty umlProperty) {
        PersistedBmmGenericType genericType = new PersistedBmmGenericType();
        UmlTemplateBinding binding = umlProperty.getFirstType().getTemplateBinding();
        String owningClass = binding.getSignature().getOwningClass().getName();
        genericType.setRootType(owningClass);
        List<String> types = new ArrayList<>();
        for(ParameterSubstitution sub : binding.getBindings()) {
            types.add(sub.getActualParameter().getName());
        }
        genericType.setGenericParameters(types);
        return genericType;
    }

    /**
     * Logic for handling the conversion of generic classes from UML to BMM. This routine makes use
     * of the UML Template Signature construct.
     *
     * TODO Investigate whether this approach is correct
     *
     * @param umlClass
     * @return
     */
    private PersistedBmmClass handleGenericClasses(UmlClass umlClass) {
        PersistedBmmClass bmmClass = new PersistedBmmClass();
        UmlTemplateSignature signature = umlClass.getTemplateSignature();
        if (signature != null) {
            for (UmlTemplateParameter param : signature.getParameters()) {
                PersistedBmmGenericParameter bmmParam = new PersistedBmmGenericParameter();
                bmmParam.setName(param.getName());
                bmmClass.addGenericParameterDefinition(bmmParam);
                if(param.getType() != null) {
                    bmmParam.setConformsToType(param.getType().getName());
                }
            }
        }
        return bmmClass;
    }
}
