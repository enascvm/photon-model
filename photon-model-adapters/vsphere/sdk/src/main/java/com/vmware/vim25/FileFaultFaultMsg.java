
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.9-b14002
 * Generated source version: 2.2
 * 
 */
@WebFault(name = "FileFaultFault", targetNamespace = "urn:vim25")
public class FileFaultFaultMsg
    extends Exception
{

    /**
     * Java type that goes as soapenv:Fault detail element.
     * 
     */
    private FileFault faultInfo;

    /**
     * 
     * @param faultInfo
     * @param message
     */
    public FileFaultFaultMsg(String message, FileFault faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @param faultInfo
     * @param cause
     * @param message
     */
    public FileFaultFaultMsg(String message, FileFault faultInfo, Throwable cause) {
        super(message, cause);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @return
     *     returns fault bean: com.vmware.vim25.FileFault
     */
    public FileFault getFaultInfo() {
        return faultInfo;
    }

}