package com.cnaude.autowhitelist;

import java.util.ArrayList;

/**
 *
 * @author cnaude
 */
public class CaseInsensitiveList extends ArrayList<String> {    
    
    @Override
    public boolean contains(Object o) {
        String paramStr = (String)o;
        for (String s : this) {
            if (paramStr.equalsIgnoreCase(s)) return true;
        }
        return false;
    }
    
    public boolean removeString(Object o) {
        String paramStr = (String)o;
        String removeStr = null;
        for (String s : this) {
            if (paramStr.equalsIgnoreCase(s)) {
                removeStr = s;
                break;
            }
        }
        if (removeStr != null) {
            this.remove(removeStr);
            return true;
        }
        return false;
    }
}
