/*
 * Created on May 11, 2006
 */
package uk.org.ponder.rsf.components;

/**
 * Represents the site of rendering of a choice from a UISelect component.
 * 
 * @author Antranig Basman (antranig@caret.cam.ac.uk)
 * 
 */

public class UISelectChoice extends UIComponent {
  public String parentFullID;

  public int choiceindex;

  public static UISelectChoice make(UIContainer tofill, String ID,
      String parentFullID, int choiceindex) {
    UISelectChoice togo = new UISelectChoice();
    togo.ID = ID;
    togo.parentFullID = parentFullID;
    togo.choiceindex = choiceindex;
    tofill.addComponent(togo);
    return togo;
  }
}
