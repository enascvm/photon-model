
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NumVirtualCpusExceedsLimit complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NumVirtualCpusExceedsLimit"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InsufficientResourcesFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="maxSupportedVcpus" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NumVirtualCpusExceedsLimit", propOrder = {
    "maxSupportedVcpus"
})
public class NumVirtualCpusExceedsLimit
    extends InsufficientResourcesFault
{

    protected int maxSupportedVcpus;

    /**
     * Gets the value of the maxSupportedVcpus property.
     * 
     */
    public int getMaxSupportedVcpus() {
        return maxSupportedVcpus;
    }

    /**
     * Sets the value of the maxSupportedVcpus property.
     * 
     */
    public void setMaxSupportedVcpus(int value) {
        this.maxSupportedVcpus = value;
    }

}
