package uk.ac.shef.dcs.jate.lucene.filter;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.TokenStream;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class ComplexShingleFilterFactory extends MWEFilterFactory {
    private final boolean sentenceBoundaryAware;
    private final boolean outputUnigrams;
    private final boolean outputUnigramsIfNoShingles;
    private final String tokenSeparator;
    private final String fillerToken;

    /** Creates a new ShingleFilterFactory */
    public ComplexShingleFilterFactory(Map<String, String> args) {
        super(args);
        maxTokens = getInt(args, "maxTokens", MWEFilter.DEFAULT_MAX_TOKENS);
        if (maxTokens < 2) {
            throw new IllegalArgumentException("Invalid maxTokens (" + maxTokens + ") - must be at least 2");
        }
        minTokens = getInt(args, "minTokens", MWEFilter.DEFAULT_MIN_TOKENS);
        if (minTokens < 2) {
            throw new IllegalArgumentException("Invalid minTokens (" + minTokens + ") - must be at least 2");
        }
        if (minTokens > maxTokens) {
            throw new IllegalArgumentException
                    ("Invalid minTokens (" + minTokens + ") - must be no greater than maxTokens (" + maxTokens + ")");
        }
        maxCharLength = getInt(args, "maxCharLength", MWEFilter.DEFAULT_MAX_CHAR_LENGTH);
        if (maxCharLength < 2) {
            throw new IllegalArgumentException("Invalid maxCharLength (" + maxCharLength + ") - must be at least 2");
        }
        minCharLength = getInt(args, "minCharLength", MWEFilter.DEFAULT_MIN_CHAR_LENGTH);
        if (minCharLength < 1) {
            throw new IllegalArgumentException("Invalid minCharLength (" + minCharLength + ") - must be at least 1");
        }
        if (minCharLength > maxCharLength) {
            throw new IllegalArgumentException
                    ("Invalid minCharLength (" + minCharLength + ") - must be no greater than maxCharLength (" + maxCharLength + ")");
        }

        String stopWordsFile=get(args,"stopWords","");
        if(!stopWordsFile.equals("")){
            try {
                Set<String> sw= new HashSet<>(FileUtils.readLines(new File(stopWordsFile)));
                String lowercase = get(args,"stopWordsIgnoreCase","");
                if(lowercase.equalsIgnoreCase("true")){
                    stopWordsIgnoreCase=true;
                    for(String s: sw)
                        stopWords.add(s.toLowerCase());
                }
                else {
                    stopWords.addAll(sw);
                    stopWordsIgnoreCase=false;
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        outputUnigrams = getBoolean(args, "outputUnigrams", true);
        outputUnigramsIfNoShingles = getBoolean(args, "outputUnigramsIfNoShingles", false);
        tokenSeparator = get(args, "tokenSeparator", ComplexShingleFilter.DEFAULT_TOKEN_SEPARATOR);
        fillerToken = get(args, "fillerToken", ComplexShingleFilter.DEFAULT_FILLER_TOKEN);

        removeLeadingStopwords=getBoolean(args, "removeLeadingStopWords", ComplexShingleFilter.DEFAULT_REMOVE_LEADING_STOPWORDS);
        removeTrailingStopwords=getBoolean(args, "removeTrailingStopWords", ComplexShingleFilter.DEFAULT_REMOVE_TRAILING_STOPWORDS);
        removeLeadingSymbolicTokens=getBoolean(args, "removeLeadingSymbolicTokens", ComplexShingleFilter.DEFAULT_REMOVE_LEADING_SYMBOLS);
        removeTrailingSymbolicTokens=getBoolean(args, "removeTrailingSymbolicTokens", ComplexShingleFilter.DEFAULT_REMOVE_TRAILING_SYMBOLS);
        sentenceBoundaryAware=getBoolean(args, "sentenceBoundaryAware", ComplexShingleFilter.DEFAULT_SENTENCE_BOUNDARY_AWARE);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    @Override
    public ComplexShingleFilter create(TokenStream input) {
        ComplexShingleFilter r = new ComplexShingleFilter(input, minTokens, maxTokens,
                minCharLength, maxCharLength,outputUnigrams, outputUnigramsIfNoShingles,
                tokenSeparator, fillerToken,
                removeLeadingStopwords, removeTrailingStopwords,
                removeLeadingSymbolicTokens, removeTrailingSymbolicTokens,
                sentenceBoundaryAware,stopWords,stopWordsIgnoreCase);
        return r;
    }
}
