
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestMappedAliases complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestMappedAliases"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="base64Cert" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="username" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="subjects" type="{urn:vim25}GuestAuthSubject" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestMappedAliases", propOrder = {
    "base64Cert",
    "username",
    "subjects"
})
public class GuestMappedAliases
    extends DynamicData
{

    @XmlElement(required = true)
    protected String base64Cert;
    @XmlElement(required = true)
    protected String username;
    @XmlElement(required = true)
    protected List<GuestAuthSubject> subjects;

    /**
     * Gets the value of the base64Cert property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBase64Cert() {
        return base64Cert;
    }

    /**
     * Sets the value of the base64Cert property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBase64Cert(String value) {
        this.base64Cert = value;
    }

    /**
     * Gets the value of the username property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the value of the username property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUsername(String value) {
        this.username = value;
    }

    /**
     * Gets the value of the subjects property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subjects property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubjects().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link GuestAuthSubject }
     * 
     * 
     */
    public List<GuestAuthSubject> getSubjects() {
        if (subjects == null) {
            subjects = new ArrayList<GuestAuthSubject>();
        }
        return this.subjects;
    }

}
