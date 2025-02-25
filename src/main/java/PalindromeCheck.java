import java.util.stream.IntStream;

public class PalindromeCheck {

    public static void main(String[] args) {
        String str = "madam"; // You can change this string to test other cases

        if (isPalindrome(str)) {
            System.out.println(str + " is a palindrome.");
        } else {
            System.out.println(str + " is not a palindrome.");
        }
    }

    public static boolean isPalindrome(String str) {
        int start = 0;
        int end = str.length() - 1;

        while (start < end) {
            if (str.charAt(start) != str.charAt(end)) {
                return false; // If characters don't match, it's not a palindrome
            }
            start++;
            end--;
        }
        return true; // The string is a palindrome
    }


    public static boolean isPalindromeUsinStreams(String str) {
        return IntStream.range(0, str.length() / 2) // Stream indices from 0 to half the length of the string
                .allMatch(i -> str.charAt(i) == str.charAt(str.length() - 1 - i)); // Check if characters match symmetrically
    }
}
