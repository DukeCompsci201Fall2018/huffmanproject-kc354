import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	private static final int BIT_PER_INT = 0;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out)
	{
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);

		/*while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}*/
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) 
	{
		while(true)
		{
			int letter = in.readBits(BITS_PER_WORD);
			if(letter == -1)
			{
				break;
			}
			else
			{
				String code = codings[letter];
				out.writeBits(code.length(), Integer.parseInt(code,2));
			}
			
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));

	}

	// Write the tree to the header
	private void writeHeader(HuffNode root, BitOutputStream out) 
	{
		HuffNode current = root;
		if(current == null)
		{
			return;
		}
		if(current.myValue == 0)
		{
			out.writeBits(1, 0);
			writeHeader(current.myLeft, out);
			writeHeader(current.myRight, out);
		}
		else if(current.myLeft == null && current.myRight == null)
		{
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}

	private String[] makeCodingsFromTree(HuffNode root) 
	{
		String[] encodings = new String[ALPH_SIZE + 1];
	    codingHelper(root,"",encodings);
		
		return encodings;
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) 
	{
		if (root == null)
		{
			//System.out.println("Root is null");
			return;
		}
		if (root.myLeft == null && root.myRight == null)
		{
			encodings[root.myValue] = path;
			if (myDebugLevel >= DEBUG_HIGH)
			{
				System.out.printf("encoding for %d is $s\n", root.myValue, path);
			}
			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	private HuffNode makeTreeFromCounts(int[] counts) 
	{
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int k = 0; k < counts.length; k++)
		{
			if(counts[k] > 0)
			{
				pq.add(new HuffNode(k, counts[k], null, null));
			}
		}
		if (myDebugLevel >= DEBUG_HIGH)
		{
			System.out.printf("pq created with %d nodes\n", pq.size());
		}
			
			
		while(pq.size() > 1)
		{
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			
			//System.out.println("left:" + left.myValue + " " + left.myWeight);
			//System.out.println("right" + right.myValue + " " + right.myWeight);
			
			HuffNode mergeW = new HuffNode(0,left.myWeight + right.myWeight, left, right);
			
			pq.add(mergeW);
		}
		
		HuffNode root = pq.remove();
		//System.out.println("root: " + root.myValue + " " + root.myWeight);
		
		return root;
	}

	// Determine Frequency
	private int[] readForCounts(BitInputStream in) 
	{
		int[] freqs = new int[ALPH_SIZE + 1];
		
		while(true)
		{
			int val = in.readBits(BITS_PER_WORD);
			
			if(val == -1)
			{
				break;
			}
			else
			{
				freqs[val] = freqs[val] + 1;
			}
		}
		
		freqs[PSEUDO_EOF] = 1;
		
		if (myDebugLevel >= DEBUG_HIGH)
		{
			for (int c = 0; c < freqs.length; c++)
			{
				if(freqs[c] > 0)
				{
					System.out.println(c + " " + freqs[c]);
				}
			}
		}
		
		return freqs;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out)
	{
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE)
		{
			throw new HuffException ("illegal header starts with " +bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();

	}

	// Read the tree used to decompress
	private HuffNode readTreeHeader(BitInputStream in) 
	{
		int bit = in.readBits(1);
		if (bit == -1)
		{
			throw new HuffException("Reading bits failed.");
		}
		else if (bit == 0)
		{
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left, right);
		}
		else
		{
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null, null);
		}
	}

	// Read the compressed bits
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) 
	{
		HuffNode current = root;
		while(true)
		{
			int bits = in.readBits(1);
			if(bits == -1)
			{
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else
			{
				if (bits == 0)
				{
					current = current.myLeft;
				}
				else
				{
					current = current.myRight;
				}
				
				if (current.myLeft == null && current.myRight == null)
				{
					if (current.myValue == PSEUDO_EOF)
					{
						break;
					}
					else
					{
						out.writeBits(BITS_PER_WORD,current.myValue);
						current = root;
					}
				}
			}
		}
	}
}