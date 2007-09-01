/*
 * Created on 1 Sep 2007
 */
package uk.org.ponder.rsf.state.entity.support;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import uk.org.ponder.beanutil.BeanLocator;
import uk.org.ponder.beanutil.PathUtil;
import uk.org.ponder.beanutil.entity.EntityBeanLocator;

/** Request-scope implementation of the EntityBeanLocator, cooperating with the
 * @link StaticEntityBeanLocatorImpl application-scope registrar. 
 * 
 * @author Antranig Basman (antranig@caret.cam.ac.uk)
 *
 */

public class EntityBeanLocatorImpl implements EntityBeanLocator {

  private StaticEntityBeanLocatorImpl sebl;

  public EntityBeanLocatorImpl(StaticEntityBeanLocatorImpl sebl) {
    this.sebl = sebl;
    this.beanlocator = sebl.RSACbeanlocator.getBeanLocator();
  }
  
  private Map delivered = new HashMap();
  
  private BeanLocator beanlocator;
  
  public Object locateBean(String name) {
    Object togo = delivered.get(name);
    if (togo == null) {
      if (name.startsWith(NEW_PREFIX)) {
        if (sebl.newEL != null) {
          togo = sebl.bma.invokeBeanMethod(sebl.newEL, beanlocator);
        }
        else {
          togo = sebl.reflectivecache.construct(sebl.entityClazz);
        }
      }
      else {
        togo = sebl.bma.invokeBeanMethod(PathUtil.composePath(sebl.fetchEL, '\'' + name + '\''),
            beanlocator);
      }
      delivered.put(name, togo);
    }
    return togo;
  }

  public void saveAll() {
    if (sebl.saveEL != null) {
      String penultimate = PathUtil.getToTailPath(sebl.saveEL);
      String savemethod = PathUtil.getTailPath(sebl.saveEL);
      Object saver = sebl.bma.getBeanValue(penultimate, beanlocator, null);

      for (Iterator it = delivered.entrySet().iterator(); it.hasNext();) {
        String key = (String) it.next();
        Object value = delivered.get(key);
        sebl.reflectivecache.invokeMethod(saver, savemethod, new Object[] { value });
      }
    }
  }

  public boolean remove(String beanname) {
    if (sebl.removeEL == null) {
      throw new UnsupportedOperationException("Cannot implement removal from EntityBeanLocator without removeEL being set");
    }
    // TODO Auto-generated method stub
    return false;
  }

  public void set(String beanname, Object toset) {
    // TODO Auto-generated method stub
    
  }
  
  public Map getDeliveredBeans() {
    return delivered;
  }
}
