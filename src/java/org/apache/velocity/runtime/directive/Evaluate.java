package org.apache.velocity.runtime.directive;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

import org.apache.velocity.context.EvaluateContext;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.context.InternalContextAdapterImpl;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.TemplateInitException;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.ParserTreeConstants;
import org.apache.velocity.runtime.parser.node.Node;
import org.apache.velocity.runtime.parser.node.SimpleNode;

/**
 * Evalutes the macro argument as a Velocity string, using the existing
 * context.
 *
 * @author <a href="mailto:wglass@apache.org">Will Glass-Husain</a>
 * @version $Id: Literal.java 471381 2006-11-05 08:56:58Z wglass $
 */
public class Evaluate extends Directive
{

    /**
     * Return name of this directive.
     * @return The name of this directive.
     */
    public String getName()
    {
        return "evaluate";
    }

    /**
     * Return type of this directive.
     * @return The type of this directive.
     */
    public int getType()
    {
        return LINE;
    }

    /**
     * Initialize and check arguments.
     * @param rs
     * @param context
     * @param node
     * @throws TemplateInitException
     */
    public void init(RuntimeServices rs, InternalContextAdapter context,
                     Node node)
        throws TemplateInitException
    {
        super.init( rs, context, node );

        /**
         * Check that there is exactly one argument and it is a string or reference.
         */  
        
        int argCount = node.jjtGetNumChildren();
        if (argCount != 1)
        {
            throw new TemplateInitException(
                    "#" + getName() + "() requires exactly one argument", 
                    context.getCurrentTemplateName(),
                    node.getColumn(),
                    node.getLine());            
        }
        
        Node childNode = node.jjtGetChild(0);
        if ( childNode.getType() !=  ParserTreeConstants.JJTSTRINGLITERAL &&
             childNode.getType() !=  ParserTreeConstants.JJTREFERENCE )
        {
           throw new TemplateInitException(
                   "#" + getName() + "()  argument must be a string literal or reference", 
                   context.getCurrentTemplateName(),
                   childNode.getColumn(),
                   childNode.getLine());
        }
    }
    
    /**
     * Evaluate the argument, convert to a String, and evaluate again 
     * (with the same context).
     * @param context
     * @param writer
     * @param node
     * @return True if the directive rendered successfully.
     * @throws IOException
     * @throws ResourceNotFoundException
     * @throws ParseErrorException 
     * @throws MethodInvocationException
     */
    public boolean render(InternalContextAdapter context, Writer writer,
            Node node) throws IOException, ResourceNotFoundException,
            ParseErrorException, MethodInvocationException
    {

        /*
         * Evaluate the string with the current context.  We know there is
         * exactly one argument and it is a string or reference.
         */
        
        Object value = node.jjtGetChild(0).value( context );
        String sourceText;
        if ( value != null )
        {
            sourceText = value.toString();
        } 
        else
        {
            sourceText = "";
        }
        
        /*
         * The new string needs to be parsed since the text has been dynamically generated.
         */
        
        Reader reader = new BufferedReader(new StringReader(sourceText));
        String templateName = context.getCurrentTemplateName();
        SimpleNode nodeTree = null;

        try
        {
            nodeTree = rsvc.parse(reader, templateName);
        }
        catch (ParseException pex)
        {
            throw  new ParseErrorException( pex );
        }
        catch (TemplateInitException pex)
        {
            throw  new ParseErrorException( pex );
        }

        /*
         * now we want to init and render.  Chain the context
         * to prevent any changes to the current context.
         */

        if (nodeTree != null)
        {
            InternalContextAdapterImpl ica =
                new InternalContextAdapterImpl( new EvaluateContext(context, rsvc) );

            ica.pushCurrentTemplateName( templateName );

            try
            {
                try
                {
                    nodeTree.init( ica, rsvc );
                }
                catch (TemplateInitException pex)
                {
                    throw  new ParseErrorException( pex );
                }

                /*
                 *  now render, and let any exceptions fly
                 */
                nodeTree.render( ica, writer );
            }
            finally
            {
                ica.popCurrentTemplateName();
            }

            return true;
        }

        
        return false;
    }

}
