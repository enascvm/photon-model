
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MksConnectionLimitReached complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MksConnectionLimitReached"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InvalidState"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="connectionLimit" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MksConnectionLimitReached", propOrder = {
    "connectionLimit"
})
public class MksConnectionLimitReached
    extends InvalidState
{

    protected int connectionLimit;

    /**
     * Gets the value of the connectionLimit property.
     * 
     */
    public int getConnectionLimit() {
        return connectionLimit;
    }

    /**
     * Sets the value of the connectionLimit property.
     * 
     */
    public void setConnectionLimit(int value) {
        this.connectionLimit = value;
    }

}
