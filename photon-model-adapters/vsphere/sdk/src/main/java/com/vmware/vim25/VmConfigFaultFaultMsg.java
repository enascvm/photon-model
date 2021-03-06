
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:06.980+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "VmConfigFaultFault", targetNamespace = "urn:vim25")
public class VmConfigFaultFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.VmConfigFault vmConfigFaultFault;

    public VmConfigFaultFaultMsg() {
        super();
    }
    
    public VmConfigFaultFaultMsg(String message) {
        super(message);
    }
    
    public VmConfigFaultFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public VmConfigFaultFaultMsg(String message, com.vmware.vim25.VmConfigFault vmConfigFaultFault) {
        super(message);
        this.vmConfigFaultFault = vmConfigFaultFault;
    }

    public VmConfigFaultFaultMsg(String message, com.vmware.vim25.VmConfigFault vmConfigFaultFault, Throwable cause) {
        super(message, cause);
        this.vmConfigFaultFault = vmConfigFaultFault;
    }

    public com.vmware.vim25.VmConfigFault getFaultInfo() {
        return this.vmConfigFaultFault;
    }
}
