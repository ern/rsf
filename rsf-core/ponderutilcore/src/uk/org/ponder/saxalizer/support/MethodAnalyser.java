package uk.org.ponder.saxalizer.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import uk.org.ponder.arrayutil.ArrayEnumeration;
import uk.org.ponder.beanutil.BeanLocator;
import uk.org.ponder.beanutil.PropertyAccessor;
import uk.org.ponder.beanutil.WriteableBeanLocator;
import uk.org.ponder.beanutil.support.BeanLocatorPropertyAccessor;
import uk.org.ponder.beanutil.support.IndexedPropertyAccessor;
import uk.org.ponder.beanutil.support.MapPropertyAccessor;
import uk.org.ponder.errorutil.PropertyException;
import uk.org.ponder.reflect.ClassGetter;
import uk.org.ponder.saxalizer.AccessMethod;
import uk.org.ponder.saxalizer.DeSAXalizable;
import uk.org.ponder.saxalizer.DeSAXalizableAttrs;
import uk.org.ponder.saxalizer.SAMSList;
import uk.org.ponder.saxalizer.SAXAccessMethodSpec;
import uk.org.ponder.saxalizer.SAXalizable;
import uk.org.ponder.saxalizer.SAXalizableAttrs;
import uk.org.ponder.saxalizer.SAXalizerMappingContext;
import uk.org.ponder.saxalizer.WBLAccessMethod;
import uk.org.ponder.saxalizer.mapping.SAXalizerMapperEntry;
import uk.org.ponder.util.Logger;
import uk.org.ponder.util.UniversalRuntimeException;

/**
 * One instance of a MethodAnalyser is stored for each SAXalizable class that
 * the SAXalizer discovers; this instance is returned when a call is made to
 * <code>getMethodAnalyser</code> with an object of the SAXalizable class as
 * argument. MethodAnalysers are cached in a static hashtable indexed by the
 * SAXalizable class.
 * <p>
 * Some "bean-sense" has been retroactively blown into this class, which dates
 * from the dinosaur SAXalizer days of 2000, with the retrofit of the
 * <code>PropertyAccessor</code> interface. Its structure still needs a little
 * work though, since it still maintains separate collections for "tag" and
 * "attribute" methods.
 */
public class MethodAnalyser implements PropertyAccessor {
  public Class targetclass;
  /**
   * Each of the four types of SAXAccessMethods supported, being get and set
   * methods for subtags and attributes.
   */
  public SAXAccessMethodHash tagmethods;
  public SAXAccessMethodHash attrmethods;
  public SAXAccessMethod bodymethod;
  /** A flat array of ALL accessors for the target class. This will be the most
   * efficient means of using introspection information, and is populated on
   * construction of this MethodAnalyser.
   */
  public SAXAccessMethod[] allgetters;

  private void assembleGetters() {
    ArrayList accumulate = new ArrayList();
    for (SAMIterator tagget = tagmethods.getGetEnumeration(); tagget.valid(); tagget
        .next()) {
      accumulate.add(tagget.get());
    }
    for (SAMIterator tagget = attrmethods.getGetEnumeration(); tagget.valid(); tagget
        .next()) {
      accumulate.add(tagget.get());
    }
    if (bodymethod != null) {
      accumulate.add(allgetters);
    }
    allgetters = new SAXAccessMethod[accumulate.size()];
    for (int i = 0; i < accumulate.size(); ++i) {
      allgetters[i] = (SAXAccessMethod) accumulate.get(i);
    }
  }

  public AccessMethod getAccessMethod(String tagname) {
    SAXAccessMethod method = tagmethods.get(tagname);
    if (method == null) {
      method = attrmethods.get(tagname);
    }
    if (method == null && WriteableBeanLocator.class.isAssignableFrom(targetclass)) {
      return new WBLAccessMethod(WriteableBeanLocator.class, tagname);
    }
    return method;
  }

  // ****** Begin implementation of PropertyAccessor interface
  public boolean canSet(String name) {
    AccessMethod accessmethod = getAccessMethod(name);
    return accessmethod == null ? false
        : accessmethod.canSet();
  }

  public void setProperty(Object parent, String name, Object value) {
   AccessMethod accessmethod = getAccessMethod(name);
    if (accessmethod == null) {
      throw UniversalRuntimeException
          .accumulate(new PropertyException(), "Property " + name
              + " of object " + parent.getClass() + " not found");
    }
    else if (!accessmethod.canSet()) {
      throw UniversalRuntimeException.accumulate(new PropertyException(), "Property " + name
              + " of object " + parent.getClass() + " is not writeable");
    }
    accessmethod.setChildObject(parent, value);
  }

  public void unlink(Object parent, String name) {
    AccessMethod accessmethod = getAccessMethod(name);
    if (accessmethod == null) {
      throw UniversalRuntimeException
          .accumulate(new PropertyException(), "Property " + name
              + " of object " + parent.getClass() + " not found");
    }
    accessmethod.setChildObject(parent, null);
  }

  public boolean canGet(String name) {
    AccessMethod accessmethod = getAccessMethod(name);
    return accessmethod == null ? false
        : accessmethod.canGet();
  }

  public Object getProperty(Object parent, String name) {
    AccessMethod accessmethod = getAccessMethod(name);
    if (accessmethod == null) {
      throw UniversalRuntimeException
          .accumulate(new PropertyException(), "Property " + name
              + " of object " + parent.getClass() + " not found");
    }
    return accessmethod.getChildObject(parent);
  }

  public Class getPropertyType(Object parent, String name) {
    AccessMethod accessmethod = getAccessMethod(name);
    if (accessmethod == null) {
      throw UniversalRuntimeException.accumulate(new PropertyException(),
          "Property " + name + " of " + targetclass + " not found ");
    }
    return accessmethod.getAccessedType();
  }

  public boolean isMultiple(Object parent, String name) {
    AccessMethod accessmethod = getAccessMethod(name);
    if (accessmethod == null) {
      throw UniversalRuntimeException.accumulate(new PropertyException(),
          "Property " + name + " of " + targetclass + " not found"); 
    }
    return accessmethod.isDenumerable();
  }

  // ****** End implementation of PropertyAccessor interface.
  /**
   * Given an object to be serialised/deserialised, return a MethodAnalyser
   * object containing a hash of Method and Field accessors. The
   * <code>context</code> stores a hash of these analysers so they are only
   * ever computed once per context per object class analysed.
   * 
   * @param objclass Either an object instance to be investigated, or an object class.
   *          If a class is specified and no analyser is registered, a new
   *          object will be created using newInstance() to be queried.
   * @param context the context
   * @return a MethodAnalyser
   */

  public static MethodAnalyser constructMethodAnalyser(Class objclass,
      SAXalizerMappingContext context) {

    SAXalizerMapperEntry entry = context.mapper.byClass(objclass);
    try {
      MethodAnalyser togo = new MethodAnalyser(objclass, entry, context);
      return togo;
    }
    catch (Exception e) {
      throw UniversalRuntimeException.accumulate(e, 
          "Error constructing method analyser for " + objclass);
    }
  }

  public static PropertyAccessor getPropertyAccessor(Object o,
      SAXalizerMappingContext context) {
    if (o instanceof BeanLocator) {
      return BeanLocatorPropertyAccessor.instance;
    }
    else if (o instanceof Map) {
      return MapPropertyAccessor.instance;
    }
    else if (IndexedPropertyAccessor.isIndexed(o.getClass())) {
      return context.getIndexedPropertyAccessor();
    }
    else
      return context.getAnalyser(o.getClass());
  }

  private void condenseMethods(SAMSList existingmethods,
      Enumeration newmethods, String xmlform) {
    while (newmethods.hasMoreElements()) {
      SAXAccessMethodSpec nextentry = (SAXAccessMethodSpec) newmethods
          .nextElement();
      // fuse together any pairs of methods that refer to the same tag/property
      // name(xmlname)
      // as getters and setters.
      if (nextentry.xmlform.equals(xmlform)) {
        SAXAccessMethodSpec previous = existingmethods
            .byXMLName(nextentry.xmlname);
        if (previous != null) {
          SAXAccessMethodSpec setmethod = null;
          if (previous.setmethodname != null)
            setmethod = previous;
          if (nextentry.setmethodname != null) {
            if (setmethod != null) {
              throw new UniversalRuntimeException(
                  "Duplicate set method specification for tag "
                      + previous.xmlname + " with java type " + previous.clazz);
            }
            setmethod = nextentry;
            previous.setmethodname = nextentry.setmethodname;
          }
          if (setmethod == null) {
            throw new UniversalRuntimeException("Neither of specifications "
                + previous + " and " + nextentry + " defines a set method");
          }
          // The "set" method will in general have a more precise argument type.
          previous.clazz = setmethod.clazz;
          if (nextentry.getmethodname != null) {
            previous.getmethodname = nextentry.getmethodname;
          }
        }
        else {
          existingmethods.add(nextentry);
        }
      }
    }
  }

  SAXAccessMethodSpec bodymethodspec = null;

  public void checkBodyMethodSpec(SAXAccessMethodSpec bodymethodspec) {
    if (bodymethodspec.xmlform.equals(SAXAccessMethodSpec.XML_BODY)) {
      if (this.bodymethodspec != null) {
        throw new UniversalRuntimeException("Duplicate body method spec "
            + bodymethodspec);
      }
      this.bodymethodspec = bodymethodspec;
    }
  }

  // Note that these two methods have side-effect on bodymethodspec
  private void absorbSAMSArray(SAXAccessMethodSpec[] setmethods,
      SAMSList tagMethods, SAMSList attrMethods) {
    condenseMethods(tagMethods, new ArrayEnumeration(setmethods),
        SAXAccessMethodSpec.XML_TAG);
    condenseMethods(attrMethods, new ArrayEnumeration(setmethods),
        SAXAccessMethodSpec.XML_ATTRIBUTE);
    if (setmethods != null) {
      for (int i = 0; i < setmethods.length; ++i) {
        checkBodyMethodSpec(setmethods[i]);
      }
    }
  }

  private void absorbSAMSList(SAXalizerMapperEntry entry, SAMSList tagMethods,
      SAMSList attrMethods) {
    condenseMethods(tagMethods, Collections.enumeration(entry.getSAMSList()),
        SAXAccessMethodSpec.XML_TAG);
    condenseMethods(attrMethods, Collections.enumeration(entry.getSAMSList()),
        SAXAccessMethodSpec.XML_ATTRIBUTE);
    for (int i = 0; i < entry.size(); ++i) {
      checkBodyMethodSpec(entry.specAt(i));
    }
  }

  /**
   * This constructor locates SAXAccessMethodSpec objects for objects of the
   * supplied class from all available static and dynamic sources, sorts them
   * into tag and attribute methods while condensing together set and get
   * specifications into single entries, and returns a MethodAnalyser object
   * with the specs resolved into Method and Field accessors ready for use.
   * 
   * @param objclass The class of the object to be inspected.
   * @param o Either the object to be inspected for accessors, or its class in
   *          the case construction is to be deferred until the last possible
   *          moment (it implements SAXalizable &c)
   * @param entry A SAXalizerMapperEntry object already determined from dynamic
   *          sources.
   * @param context The global mapping context.
   */
  MethodAnalyser(Class objclass, SAXalizerMapperEntry entry,
      SAXalizerMappingContext context) {
    targetclass = objclass;
    bodymethodspec = null;
    SAMSList tagMethods = new SAMSList();
    SAMSList attrMethods = new SAMSList();
    boolean defaultinferrible = context.inferrer != null
        && (context.inferrer.isDefaultInferrible(objclass) || entry != null
            && entry.defaultible);
    // source 1: dynamic info from mapper file takes precendence
    if (entry != null) {
      // do not absorb entry if defaultinferrible, since it will be done again
      // later.
      if (!defaultinferrible) {
        absorbSAMSList(entry, tagMethods, attrMethods);
      }
    }
    else {
      if (SAXalizable.class.isAssignableFrom(objclass)
          || SAXalizableAttrs.class.isAssignableFrom(objclass)
          || DeSAXalizable.class.isAssignableFrom(objclass)
          || DeSAXalizableAttrs.class.isAssignableFrom(objclass)) {
        // this branch will become gradually more deprecated - info should move
        // into mapping files or else be default.
        Object o = ClassGetter.construct(objclass);
        // source 2: static info from interfaces is second choice
        // System.out.println("MethodAnalyser called for object "+o);
        if (o instanceof SAXalizable) {
          SAXalizable so = (SAXalizable) o;
          SAXAccessMethodSpec[] setMethods = so.getSAXSetMethods();
          SAXAccessMethodSpec.convertToSetSpec(setMethods);
          absorbSAMSArray(setMethods, tagMethods, attrMethods);
        }
        if (o instanceof SAXalizableAttrs) { // now do the same for attributes
          SAXalizableAttrs sao = (SAXalizableAttrs) o;
          SAXAccessMethodSpec[] setAttrMethods = sao.getSAXSetAttrMethods();
          if (setAttrMethods != null) {
            Logger.println("MethodAnalyser found " + setAttrMethods.length
                + " setattr methods for " + o.getClass(),
                Logger.DEBUG_INFORMATIONAL);
          }
          SAXAccessMethodSpec.convertToAttrSpec(setAttrMethods);
          SAXAccessMethodSpec.convertToSetSpec(setAttrMethods);
          absorbSAMSArray(setAttrMethods, tagMethods, attrMethods);
        }
        if (o instanceof DeSAXalizable) {
          // construct array of SAXAccessMethods for DeSAXalizable objects
          DeSAXalizable doz = (DeSAXalizable) o;
          SAXAccessMethodSpec[] getMethods = doz.getSAXGetMethods();
          absorbSAMSArray(getMethods, tagMethods, attrMethods);
        }
        if (o instanceof DeSAXalizableAttrs) { // now do the same for
                                                // attributes
          DeSAXalizableAttrs sao = (DeSAXalizableAttrs) o;
          SAXAccessMethodSpec[] getAttrMethods = sao.getSAXGetAttrMethods();
          if (getAttrMethods != null) {
            SAXAccessMethodSpec.convertToAttrSpec(getAttrMethods);
            Logger.println("MethodAnalyser found " + getAttrMethods.length
                + " getattr methods for " + o, Logger.DEBUG_INFORMATIONAL);
          }
          absorbSAMSArray(getAttrMethods, tagMethods, attrMethods);
        }
      }
    }
    // Source 3: if no accessors have so far been discovered, try to infer some
    // using an inferrer if one is set.
    if (context.inferrer != null
        && (tagMethods.size() == 0 && attrMethods.size() == 0)
        || defaultinferrible) {
      entry = context.inferrer.inferEntry(objclass, entry);
      absorbSAMSList(entry, tagMethods, attrMethods);
    }

    tagmethods = new SAXAccessMethodHash(tagMethods, objclass);
    attrmethods = new SAXAccessMethodHash(attrMethods, objclass);
    if (bodymethodspec != null) {
      bodymethod = new SAXAccessMethod(bodymethodspec, objclass);
    }
    bodymethodspec = null;
    assembleGetters();
  }

}