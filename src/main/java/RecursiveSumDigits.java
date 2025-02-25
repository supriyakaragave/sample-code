public class RecursiveSumDigits {

    public static void main(String[] args) {
        int number = 9876; // You can change this number to test others

        int result = sumDigits(number);
        System.out.println("Sum of digits: " + result);
    }

    // Recursive method to sum the digits of a number
    public static int sumDigits(int number) {
        // Base case: If the number is less than 10, return the number itself
        if (number < 10) {
            return number;
        }

        // Recursive case: Sum the last digit with the sum of the remaining digits
        return number % 10 + sumDigits(number / 10);
    }

    public static int sumDigitsUsingStreams(int number) {
        return Integer.toString(number) // Convert the number to a string
                .chars() // Get an IntStream of character codes
                .map(Character::getNumericValue) // Convert each character to its numeric value
                .sum(); // Sum the values
    }
}
