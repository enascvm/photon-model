
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RunScriptAction complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RunScriptAction"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Action"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="script" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RunScriptAction", propOrder = {
    "script"
})
public class RunScriptAction
    extends Action
{

    @XmlElement(required = true)
    protected String script;

    /**
     * Gets the value of the script property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getScript() {
        return script;
    }

    /**
     * Sets the value of the script property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setScript(String value) {
        this.script = value;
    }

}
