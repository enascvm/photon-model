
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestAuthentication complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestAuthentication"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="interactiveSession" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestAuthentication", propOrder = {
    "interactiveSession"
})
@XmlSeeAlso({
    NamePasswordAuthentication.class,
    SAMLTokenAuthentication.class,
    SSPIAuthentication.class,
    TicketedSessionAuthentication.class
})
public class GuestAuthentication
    extends DynamicData
{

    protected boolean interactiveSession;

    /**
     * Gets the value of the interactiveSession property.
     * 
     */
    public boolean isInteractiveSession() {
        return interactiveSession;
    }

    /**
     * Sets the value of the interactiveSession property.
     * 
     */
    public void setInteractiveSession(boolean value) {
        this.interactiveSession = value;
    }

}
